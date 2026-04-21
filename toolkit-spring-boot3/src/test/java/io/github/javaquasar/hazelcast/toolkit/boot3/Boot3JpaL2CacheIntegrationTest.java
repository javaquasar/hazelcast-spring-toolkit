package io.github.javaquasar.hazelcast.toolkit.boot3;

import com.hazelcast.cache.ICache;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.nearcache.NearCacheStats;
import io.github.javaquasar.hazelcast.toolkit.boot3.l2.L2CacheTestConfiguration;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientFactory;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.ReflectionsClassScanner;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = SharedTestApplication.class,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create",
                "spring.jpa.open-in-view=false",
                "hazelcast.toolkit.hibernate.l2.enabled=true",
                // extended-config=true: apply full JCache wiring (region.factory_class, CacheManager binding, etc.)
                // Without this only use_second_level_cache=true would be set (safe default).
                "hazelcast.toolkit.hibernate.l2.extended-config=true",
                "hazelcast.toolkit.hibernate.l2.use-statistics=true",
                // Unique client name prevents InvalidConfigurationException when multiple test contexts
                // start in the same JVM (Hazelcast forbids duplicate instance names per JVM)
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

        // Capture the Hazelcast-internal full name (includes URI prefix, e.g. "/test-entity-region")
        // so the remote client can look it up directly without relying on JCache CacheManager scoping.
        String l2CacheName = hazelcastCache.getName();

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertTrue(hazelcastCache.getLocalCacheStatistics().getNearCacheStatistics().getOwnedEntryCount() > 0));

        hazelcastCache.get(cacheKey);

        NearCacheStats statsBeforeRemoteUpdate = hazelcastCache.getLocalCacheStatistics().getNearCacheStatistics();
        long hitsBeforeRemoteUpdate = statsBeforeRemoteUpdate.getHits();
        long missesBeforeRemoteUpdate = statsBeforeRemoteUpdate.getMisses();

        try (RemoteCacheAccess remoteCacheAccess = openRemoteCacheAccess(l2CacheName)) {
            remoteCacheAccess.cache().remove(cacheKey);
        }

        // After the remote eviction the distributed entry is gone; the local near-cache must
        // be invalidated (invalidateOnChange=true) so the next get returns null, not stale data.
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertNull(hazelcastCache.get(cacheKey)));

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

    /**
     * Opens a remote Hazelcast client connected to the same cluster and returns a handle to the
     * named ICache for direct eviction. Uses {@code HazelcastInstance.getCacheManager().getCache()}
     * (Hazelcast's ICacheManager, not JCache's CacheManager) so that the lookup is a direct
     * distributed-object access by full name rather than a JCache-scoped registry lookup.
     * JCache CacheManager.getCache() only knows caches created through that specific manager
     * instance and returns null for caches created by other clients — which is the failure mode
     * this method avoids.
     */
    private RemoteCacheAccess openRemoteCacheAccess(String l2CacheName) {
        HazelcastInstance remoteClient = new HazelcastClientFactory(new ReflectionsClassScanner(), List.of()).createClient(
                "boot3-l2-remote-client",
                hazelcastClusterName(),
                hazelcastMembers(),
                false,
                null
        );

        ICache<Object, Object> remoteCache = remoteClient.getCacheManager().getCache(l2CacheName);
        assertNotNull(remoteCache, "Remote client could not find ICache by name: " + l2CacheName);

        return new RemoteCacheAccess(remoteClient, remoteCache);
    }

    private record RemoteCacheAccess(HazelcastInstance hazelcastInstance, ICache<Object, Object> cache) implements AutoCloseable {

        @Override
        public void close() {
            hazelcastInstance.shutdown();
        }
    }
}
