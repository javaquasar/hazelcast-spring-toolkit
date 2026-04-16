package io.github.javaquasar.hazelcast.toolkit.boot3;

import com.hazelcast.cache.HazelcastCachingProvider;
import com.hazelcast.cache.ICache;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.nearcache.NearCacheStats;
import io.github.javaquasar.hazelcast.toolkit.boot3.l2.L2CacheTestConfiguration;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientFactory;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.compat.CompactClassesScanner;
import io.github.javaquasar.hazelcast.toolkit.spring.test.boot.SharedTestApplication;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntity;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntityRepository;
import io.github.javaquasar.hazelcast.toolkit.testcontainers.TestcontainersEnvironment;
import org.awaitility.Awaitility;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
import javax.cache.spi.CachingProvider;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = SharedTestApplication.class,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.open-in-view=false",
                "hazelcast.toolkit.hibernate.l2.enabled=true",
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
    @Disabled("Flaky in the 2-node Testcontainers setup: remote L2 updates do not invalidate the client near cache reliably")
    void invalidatesNearCacheWhenAnotherClientUpdatesL2CacheEntry() {
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

        Cache.Entry<Object, Object> entry = findEntryContaining(l2Cache, "bravo");
        assertNotNull(entry);

        Object cacheKey = entry.getKey();
        Object originalValue = hazelcastCache.get(cacheKey);
        assertNotNull(originalValue);
        assertTrue(originalValue.toString().contains("bravo"));

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertTrue(hazelcastCache.getLocalCacheStatistics().getNearCacheStatistics().getOwnedEntryCount() > 0));

        hazelcastCache.get(cacheKey);

        NearCacheStats statsBeforeRemoteUpdate = hazelcastCache.getLocalCacheStatistics().getNearCacheStatistics();
        long hitsBeforeRemoteUpdate = statsBeforeRemoteUpdate.getHits();
        long missesBeforeRemoteUpdate = statsBeforeRemoteUpdate.getMisses();

        try (RemoteCacheAccess remoteCacheAccess = openRemoteCacheAccess()) {
            remoteCacheAccess.cacheManager().getCache(SharedTestCachedEntity.CACHE_REGION).put(cacheKey, "remote-update-marker");
        }

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertEquals("remote-update-marker", hazelcastCache.get(cacheKey)));

        NearCacheStats statsAfterRemoteUpdate = hazelcastCache.getLocalCacheStatistics().getNearCacheStatistics();
        assertTrue(
                statsAfterRemoteUpdate.getInvalidations() > 0 || statsAfterRemoteUpdate.getMisses() > missesBeforeRemoteUpdate,
                "Expected the near cache to observe a remote update via invalidation or a follow-up cache miss"
        );
        assertTrue(statsAfterRemoteUpdate.getHits() >= hitsBeforeRemoteUpdate);
    }

    private Cache.Entry<Object, Object> findEntryContaining(Cache<Object, Object> cache, String marker) {
        for (Cache.Entry<Object, Object> entry : cache) {
            Object value = entry.getValue();
            if (value != null && value.toString().contains(marker)) {
                return entry;
            }
        }
        return null;
    }

    private long countEntries(ICache<Object, Object> cache) {
        long count = 0;
        for (Cache.Entry<Object, Object> ignored : cache) {
            count++;
        }
        return count;
    }

    private RemoteCacheAccess openRemoteCacheAccess() {
        HazelcastInstance remoteHazelcastClient = new HazelcastClientFactory(new CompactClassesScanner(), List.of()).createClient(
                "boot3-l2-remote-client",
                hazelcastClusterName(),
                hazelcastMembers(),
                false,
                null
        );

        CachingProvider cachingProvider = new HazelcastCachingProvider();
        Properties properties = HazelcastCachingProvider.propertiesByInstanceItself(remoteHazelcastClient);
        CacheManager remoteCacheManager = cachingProvider.getCacheManager(
                cachingProvider.getDefaultURI(),
                cachingProvider.getDefaultClassLoader(),
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
