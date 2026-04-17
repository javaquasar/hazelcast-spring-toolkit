package io.github.javaquasar.hazelcast.toolkit.boot4;

import com.hazelcast.cache.ICache;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.boot4.l2.Boot4L2CacheTestConfiguration;
import io.github.javaquasar.hazelcast.toolkit.spring.test.boot.SharedTestApplication;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntity;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntityRepository;
import org.awaitility.Awaitility;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManagerFactory;
import javax.cache.Cache;
import javax.cache.CacheManager;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test verifying that Hibernate second-level cache works end-to-end
 * under the Boot 4 auto-configuration stack.
 *
 * <p>Uses an in-process Hazelcast member (via {@link Boot4L2CacheTestConfiguration}) and an
 * H2 in-memory database. The near-cache customizer in {@link Boot4L2CacheTestConfiguration}
 * is active by default ({@code test.hazelcast.near-cache.enabled=true}).
 */
@SpringBootTest(
        classes = SharedTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:boot4jpal2;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create",
                "spring.jpa.open-in-view=false",
                "hazelcast.toolkit.hibernate.l2.enabled=true",
                "hazelcast.toolkit.hibernate.l2.extended-config=true",
                "hazelcast.toolkit.hibernate.l2.use-statistics=true",
                "hazelcast.client.instance-name=boot4-jpa-l2-test",
                "hazelcast.client.cluster-name=" + Boot4L2CacheTestConfiguration.CLUSTER_NAME,
                "hazelcast.client.network.smart-routing=false"
        }
)
@Import(Boot4L2CacheTestConfiguration.class)
class Boot4JpaL2CacheIntegrationTest {

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
    private EntityManagerFactory entityManagerFactory;

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

        Long entityId = transactionTemplate.execute(status -> repository.save(new SharedTestCachedEntity("boot4-l2")).getId());
        assertNotNull(entityId);

        Integer rowCount = jdbcTemplate.queryForObject(
                "select count(*) from test_cached_entities where id = ?",
                Integer.class,
                entityId
        );
        assertEquals(1, rowCount);

        SharedTestCachedEntity firstRead = transactionTemplate.execute(status -> repository.findById(entityId).orElseThrow());
        assertNotNull(firstRead);
        assertEquals("boot4-l2", firstRead.getName());

        Cache<Object, Object> l2Cache = cacheManager.getCache(SharedTestCachedEntity.CACHE_REGION);
        assertNotNull(l2Cache);

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertTrue(countEntries(l2Cache.unwrap(ICache.class)) > 0));

        long hitCountBeforeSecondRead = statistics.getSecondLevelCacheHitCount();
        SharedTestCachedEntity secondRead = transactionTemplate.execute(status -> repository.findById(entityId).orElseThrow());

        assertNotNull(secondRead);
        assertEquals("boot4-l2", secondRead.getName());
        assertTrue(statistics.getSecondLevelCachePutCount() > 0,
                "Hibernate must have put the entity into the L2 cache");
        assertTrue(statistics.getSecondLevelCacheHitCount() > hitCountBeforeSecondRead,
                "Second read must be served from the L2 cache (hit count must increase)");

        assertTrue(
                hazelcastInstance.getDistributedObjects().stream()
                        .anyMatch(object -> object.getName().endsWith(SharedTestCachedEntity.CACHE_REGION)),
                "L2 cache region must be visible as a Hazelcast distributed object"
        );
    }

    private long countEntries(ICache<Object, Object> cache) {
        long count = 0;
        for (Cache.Entry<Object, Object> ignored : cache) {
            count++;
        }
        return count;
    }
}
