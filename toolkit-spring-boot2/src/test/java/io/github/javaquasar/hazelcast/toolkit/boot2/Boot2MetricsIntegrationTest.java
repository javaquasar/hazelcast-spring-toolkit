package io.github.javaquasar.hazelcast.toolkit.boot2;

import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.boot2.l2.Boot2L2CacheTestConfiguration;
import io.github.javaquasar.hazelcast.toolkit.metrics.spring.HzToolkitMetricsController;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntity;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntityRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = Boot2TestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:boot2metrics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.open-in-view=false",
                "hazelcast.toolkit.hibernate.l2.enabled=true",
                "hazelcast.toolkit.hibernate.l2.extended-config=true",
                "hazelcast.toolkit.hibernate.l2.use-statistics=true",
                "hazelcast.toolkit.metrics.enabled=true",
                "hazelcast.toolkit.metrics.diagnostic-endpoint.enabled=true",
                "test.hazelcast.near-cache.enabled=true",
                "hazelcast.client.instance-name=boot2-metrics-test",
                "hazelcast.client.cluster-name=boot2-l2-test-cluster",
                "hazelcast.client.network.smart-routing=false"
        }
)
@Import({Boot2L2CacheTestConfiguration.class, Boot2MetricsIntegrationTest.MetricsTestConfiguration.class})
class Boot2MetricsIntegrationTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private List<MeterBinder> meterBinders;

    @Autowired
    private HzToolkitMetricsController diagnosticController;

    @Autowired
    private SharedTestCachedEntityRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void bindMeters() {
        meterBinders.forEach(binder -> binder.bindTo(meterRegistry));
    }

    @Test
    void registersDiagnosticControllerSeparatelyFromMicrometerMetrics() {
        assertNotNull(diagnosticController);
        assertNotNull(meterRegistry);
    }

    @Test
    void jcacheNearStatsReturnsDisabledSectionWhenNearCacheIsNotEnabled() {
        String cacheName = "boot2-no-near-cache";
        if (cacheManager.getCache(cacheName) == null) {
            cacheManager.createCache(cacheName, new MutableConfiguration<>());
        }

        Map<String, Object> result = diagnosticController.nearJCacheStats(cacheName);

        assertEquals("OK", result.get("status"));
        assertEquals(cacheName, result.get("name"));
        assertEquals(cacheName, result.get("cacheName"));
        assertNotNull(result.get("local"), "Local cache stats should still be returned when available");

        @SuppressWarnings("unchecked")
        Map<String, Object> near = (Map<String, Object>) result.get("near");
        assertNotNull(near);
        assertEquals(false, near.get("enabled"));
    }

    @Test
    void jcacheNearStatsReturnsStandardErrorContractWhenCacheIsMissing() {
        String cacheName = "boot2-missing-cache";

        Map<String, Object> result = diagnosticController.nearJCacheStats(cacheName);

        assertEquals("ERROR", result.get("status"));
        assertEquals(cacheName, result.get("name"));
        assertEquals(cacheName, result.get("cacheName"));
        assertTrue(result.get("error").toString().contains("Cache not found"));

        @SuppressWarnings("unchecked")
        Map<String, Object> local = (Map<String, Object>) result.get("local");
        assertEquals(false, local.get("available"));

        @SuppressWarnings("unchecked")
        Map<String, Object> near = (Map<String, Object>) result.get("near");
        assertEquals(false, near.get("enabled"));
    }

    @Test
    void publishesHibernateAndNearCacheMetersAndAutoRegistersRuntimeCaches() {
        Long entityId = transactionTemplate.execute(
                status -> repository.save(new SharedTestCachedEntity("boot2-metrics")).getId()
        );
        assertNotNull(entityId);

        transactionTemplate.execute(status -> repository.findById(entityId).orElseThrow());
        transactionTemplate.execute(status -> repository.findById(entityId).orElseThrow());

        assertTrue(!meterRegistry.find("hazelcast.toolkit.hibernate.l2.hit.count").meters().isEmpty());
        assertTrue(!meterRegistry.find("hazelcast.toolkit.hibernate.l2.miss.count").meters().isEmpty());

        Gauge regionGauge = waitForGauge("hazelcast.toolkit.near.cache.enabled", SharedTestCachedEntity.CACHE_REGION, "jcache");
        assertNotNull(regionGauge);
        assertTrue(regionGauge.value() >= 0.0d);

        String runtimeMapName = "boot2-runtime-map";
        hazelcastInstance.getMap(runtimeMapName).put("key", "value");
        Gauge runtimeMapGauge = waitForGauge("hazelcast.toolkit.near.cache.enabled", runtimeMapName, "imap");
        assertNotNull(runtimeMapGauge);

        String runtimeCacheName = "boot2-runtime-cache";
        cacheManager.createCache(runtimeCacheName, new MutableConfiguration<>());
        Gauge runtimeCacheGauge = waitForGauge("hazelcast.toolkit.near.cache.enabled", runtimeCacheName, "jcache");
        assertNotNull(runtimeCacheGauge);
    }

    private Gauge waitForGauge(String metricName, String cacheName, String kind) {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            Gauge gauge = meterRegistry.find(metricName)
                    .tags("cache", cacheName, "kind", kind)
                    .gauge();
            if (gauge != null) {
                return gauge;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for meter registration", ex);
            }
        }
        return null;
    }

    @TestConfiguration
    static class MetricsTestConfiguration {
        @Bean
        SimpleMeterRegistry simpleMeterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
