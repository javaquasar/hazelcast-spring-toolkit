package io.github.javaquasar.hazelcast.toolkit.boot3;

import com.hazelcast.cache.ICache;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.boot3.l2.L2CacheTestConfiguration;
import io.github.javaquasar.hazelcast.toolkit.boot3.l2.RecordingCacheEntryListener;
import io.github.javaquasar.hazelcast.toolkit.boot3.l2.TestCachedEntity;
import io.github.javaquasar.hazelcast.toolkit.boot3.l2.TestCachedEntityRepository;
import io.github.javaquasar.hazelcast.toolkit.testcontainers.TestcontainersEnvironment;
import org.awaitility.Awaitility;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.cache.Cache;
import javax.cache.CacheManager;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = TestApplication.class,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.open-in-view=false"
        }
)
@Import(L2CacheTestConfiguration.class)
class Boot3JpaL2CacheIntegrationTest extends TestcontainersEnvironment {

    @Autowired
    private TestCachedEntityRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @Autowired
    private jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        TestcontainersEnvironment.registerSpringProperties(registry);
    }

    @Test
    void storesJpaEntityInHazelcastL2CacheAndPublishesCacheEvent() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        RecordingCacheEntryListener.reset();

        Long entityId = transactionTemplate.execute(status -> repository.save(new TestCachedEntity("alpha")).getId());
        assertNotNull(entityId);

        Integer rowCount = jdbcTemplate.queryForObject(
                "select count(*) from test_cached_entities where id = ?",
                Integer.class,
                entityId
        );
        assertEquals(1, rowCount);

        TestCachedEntity firstRead = transactionTemplate.execute(status -> repository.findById(entityId).orElseThrow());
        assertNotNull(firstRead);
        assertEquals("alpha", firstRead.getName());

        Cache<Object, Object> l2Cache = cacheManager.getCache(TestCachedEntity.CACHE_REGION);
        assertNotNull(l2Cache);

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    assertTrue(countEntries(l2Cache.unwrap(ICache.class)) > 0);
                    assertTrue(RecordingCacheEntryListener.totalEvents() > 0);
                });

        long hitCountBeforeSecondRead = statistics.getSecondLevelCacheHitCount();
        TestCachedEntity secondRead = transactionTemplate.execute(status -> repository.findById(entityId).orElseThrow());

        assertNotNull(secondRead);
        assertEquals("alpha", secondRead.getName());
        assertTrue(statistics.getSecondLevelCachePutCount() > 0);
        assertTrue(statistics.getSecondLevelCacheHitCount() > hitCountBeforeSecondRead);

        List<String> distributedObjects = hazelcastInstance.getDistributedObjects().stream()
                .map(object -> object.getServiceName() + ":" + object.getName())
                .toList();
        System.out.println("HZ distributed objects: " + distributedObjects);
        assertTrue(distributedObjects.stream().anyMatch(name -> name.endsWith(TestCachedEntity.CACHE_REGION)));
    }

    private long countEntries(ICache<Object, Object> cache) {
        long count = 0;
        for (Cache.Entry<Object, Object> ignored : cache) {
            count++;
        }
        return count;
    }
}
