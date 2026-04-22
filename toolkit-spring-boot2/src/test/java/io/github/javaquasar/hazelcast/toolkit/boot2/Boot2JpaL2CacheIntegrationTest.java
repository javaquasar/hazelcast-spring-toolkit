package io.github.javaquasar.hazelcast.toolkit.boot2;

import com.hazelcast.cache.HazelcastCachingProvider;
import com.hazelcast.cache.ICache;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.nearcache.NearCacheStats;
import io.github.javaquasar.hazelcast.toolkit.boot2.l2.Boot2L2CacheTestConfiguration;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientFactory;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.ReflectionsClassScanner;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.spi.CachingProvider;
import javax.persistence.EntityManagerFactory;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@SpringBootTest(
        classes = Boot2TestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:boot2l2;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.open-in-view=false",
                "hazelcast.toolkit.hibernate.l2.enabled=true",
                // extended-config=true: apply full JCache wiring (region.factory_class, CacheManager binding, etc.)
                // Without this only use_second_level_cache=true would be set (safe default).
                "hazelcast.toolkit.hibernate.l2.extended-config=true",
                "hazelcast.toolkit.hibernate.l2.use-statistics=true",
                "hazelcast.client.instance-name=boot2-l2-client",
                "hazelcast.client.cluster-name=boot2-l2-test-cluster",
                "hazelcast.client.network.cluster-members[0]=127.0.0.1:5701",
                "hazelcast.client.network.smart-routing=false"
        }
)
@Import(Boot2L2CacheTestConfiguration.class)
class Boot2JpaL2CacheIntegrationTest {

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

    @Test
    void invalidatesNearCacheWhenAnotherClientEvictsL2CacheEntry() {
        Long entityId = transactionTemplate.execute(status -> repository.save(new SharedTestCachedEntity("bravo")).getId());
        assertNotNull(entityId);

        SharedTestCachedEntity firstRead = transactionTemplate.execute(status -> repository.findById(entityId).orElseThrow());
        assertEquals("bravo", firstRead.getName());

        Cache<Object, Object> l2Cache = cacheManager.getCache(SharedTestCachedEntity.CACHE_REGION);
        assertNotNull(l2Cache);

        ICache<Object, Object> hazelcastCache = l2Cache.unwrap(ICache.class);
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertTrue(countEntries(hazelcastCache) > 0));

        // After clearL2CacheRegion() + saving exactly one entity, there is exactly one entry
        // in the cache. Take its key directly — no assumption about the key format is needed.
        Cache.Entry<Object, Object> anyEntry = null;
        for (Cache.Entry<Object, Object> e : l2Cache) {
            anyEntry = e;
            break;
        }
        assertNotNull(anyEntry, "Expected at least one L2 cache entry after first read of entity " + entityId);
        Object cacheKey = anyEntry.getKey();
        Object originalValue = hazelcastCache.get(cacheKey);
        assertNotNull(originalValue, "Expected a cached value for key: " + cacheKey);

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertTrue(hazelcastCache.getLocalCacheStatistics().getNearCacheStatistics().getOwnedEntryCount() > 0));

        hazelcastCache.get(cacheKey);

        NearCacheStats statsBeforeRemoteUpdate = hazelcastCache.getLocalCacheStatistics().getNearCacheStatistics();
        long hitsBeforeRemoteUpdate = statsBeforeRemoteUpdate.getHits();
        long missesBeforeRemoteUpdate = statsBeforeRemoteUpdate.getMisses();

        try (RemoteCacheAccess remoteCacheAccess = openRemoteCacheAccess()) {
            Cache<Object, Object> remoteCache = remoteCacheAccess.cacheManager().getCache(SharedTestCachedEntity.CACHE_REGION);
            assertNotNull(remoteCache, "Expected remote CacheManager to resolve the Hibernate L2 region");

            Awaitility.await()
                    .atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertNotNull(remoteCache.get(cacheKey)));

            remoteCache.remove(cacheKey);

            // Keep the remote client alive until the invalidation is observed locally.
            Awaitility.await()
                    .atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertNull(hazelcastCache.get(cacheKey)));
        }

        NearCacheStats statsAfterRemoteEviction = hazelcastCache.getLocalCacheStatistics().getNearCacheStatistics();
        assertTrue(
                statsAfterRemoteEviction.getInvalidations() > statsBeforeRemoteUpdate.getInvalidations()
                        || statsAfterRemoteEviction.getMisses() > missesBeforeRemoteUpdate,
                "Expected near-cache to observe the remote eviction via invalidation or a follow-up miss"
        );
        assertTrue(statsAfterRemoteEviction.getHits() >= hitsBeforeRemoteUpdate);
    }

    private long countEntries(ICache<Object, Object> cache) {
        long count = 0;
        for (Cache.Entry<Object, Object> ignored : cache) {
            count++;
        }
        return count;
    }

    private RemoteCacheAccess openRemoteCacheAccess() {
        HazelcastInstance remoteHazelcastClient = new HazelcastClientFactory(new ReflectionsClassScanner(), List.of()).createClient(
                "boot2-l2-remote-client-" + UUID.randomUUID(),
                Boot2L2CacheTestConfiguration.CLUSTER_NAME,
                List.of(Boot2L2CacheTestConfiguration.MEMBER_ADDRESS),
                false,
                null
        );

        CachingProvider cachingProvider = new HazelcastCachingProvider();
        Properties properties = HazelcastCachingProvider.propertiesByInstanceItself(remoteHazelcastClient);
        CacheManager remoteCacheManager = cachingProvider.getCacheManager(
                cacheManager.getURI(),
                cacheManager.getClassLoader(),
                properties
        );

        return new RemoteCacheAccess(remoteHazelcastClient, remoteCacheManager);
    }

    private record RemoteCacheAccess(HazelcastInstance hazelcastInstance, CacheManager cacheManager) implements AutoCloseable {

        @Override
        public void close() {
            cacheManager.close();
            hazelcastInstance.shutdown();
        }
    }
}
