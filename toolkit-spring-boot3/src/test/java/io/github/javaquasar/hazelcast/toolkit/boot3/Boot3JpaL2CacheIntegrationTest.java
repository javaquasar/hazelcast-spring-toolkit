package io.github.javaquasar.hazelcast.toolkit.boot3;

import com.hazelcast.cache.ICache;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.boot3.l2.L2CacheTestConfiguration;
import io.github.javaquasar.hazelcast.toolkit.spring.test.boot.SharedTestApplication;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntity;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntityRepository;
import io.github.javaquasar.hazelcast.toolkit.testcontainers.TestcontainersEnvironment;
import org.awaitility.Awaitility;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@SpringBootTest(
        classes = SharedTestApplication.class,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create",
                "spring.jpa.open-in-view=false",
                "hazelcast.toolkit.hibernate.l2.enabled=true",
                "hazelcast.toolkit.hibernate.l2.extended-config=true",
                "hazelcast.toolkit.hibernate.l2.use-statistics=true",
                "hazelcast.client.instance-name=boot3-jpa-l2-test"
        }
)
@Import(L2CacheTestConfiguration.class)
class Boot3JpaL2CacheIntegrationTest extends TestcontainersEnvironment {

    @Autowired
    private SharedTestCachedEntityRepository repository;

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

    @BeforeEach
    void clearL2CacheRegion() {
        Cache<Object, Object> cache = cacheManager.getCache(SharedTestCachedEntity.CACHE_REGION);
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    void storesJpaEntityInHazelcastL2Cache() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        Long entityId = transactionTemplate.execute(status -> repository.save(new SharedTestCachedEntity("alpha")).getId());
        assertNotNull(entityId);

        Integer rowCount = jdbcTemplate.queryForObject(
                "select count(*) from test_cached_entities where id = ?",
                Integer.class,
                entityId
        );
        assertEquals(1, rowCount);

        SharedTestCachedEntity firstRead = transactionTemplate.execute(status -> repository.findById(entityId).orElseThrow());
        assertNotNull(firstRead);
        assertEquals("alpha", firstRead.getName());

        Cache<Object, Object> l2Cache = cacheManager.getCache(SharedTestCachedEntity.CACHE_REGION);
        assertNotNull(l2Cache);

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertTrue(countEntries(l2Cache.unwrap(ICache.class)) > 0));

        long hitCountBeforeSecondRead = statistics.getSecondLevelCacheHitCount();
        SharedTestCachedEntity secondRead = transactionTemplate.execute(status -> repository.findById(entityId).orElseThrow());

        assertNotNull(secondRead);
        assertEquals("alpha", secondRead.getName());
        assertTrue(statistics.getSecondLevelCachePutCount() > 0);
        assertTrue(statistics.getSecondLevelCacheHitCount() > hitCountBeforeSecondRead);

        List<String> distributedObjects = hazelcastInstance.getDistributedObjects().stream()
                .map(object -> object.getServiceName() + ":" + object.getName())
                .toList();
        assertTrue(distributedObjects.stream().anyMatch(name -> name.endsWith(SharedTestCachedEntity.CACHE_REGION)));
    }

    private long countEntries(ICache<Object, Object> cache) {
        long count = 0;
        for (Cache.Entry<Object, Object> ignored : cache) {
            count++;
        }
        return count;
    }
}
