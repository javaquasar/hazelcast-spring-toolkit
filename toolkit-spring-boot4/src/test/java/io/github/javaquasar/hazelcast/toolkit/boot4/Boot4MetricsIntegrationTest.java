package io.github.javaquasar.hazelcast.toolkit.boot4;

import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.boot4.l2.Boot4L2CacheTestConfiguration;
import io.github.javaquasar.hazelcast.toolkit.metrics.spring.HzToolkitMetricsController;
import io.github.javaquasar.hazelcast.toolkit.spring.test.boot.SharedTestApplication;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntity;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntityRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = SharedTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:boot4metrics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create",
                "spring.jpa.open-in-view=false",
                "hazelcast.toolkit.hibernate.l2.enabled=true",
                "hazelcast.toolkit.hibernate.l2.extended-config=true",
                "hazelcast.toolkit.hibernate.l2.use-statistics=true",
                "hazelcast.toolkit.metrics.enabled=true",
                "hazelcast.toolkit.metrics.diagnostic-endpoint.enabled=true",
                "test.hazelcast.near-cache.enabled=true",
                "hazelcast.client.instance-name=boot4-metrics-test",
                "hazelcast.client.cluster-name=" + Boot4L2CacheTestConfiguration.CLUSTER_NAME,
                "hazelcast.client.network.smart-routing=false"
        }
)
@Import({Boot4L2CacheTestConfiguration.class, Boot4MetricsIntegrationTest.MetricsTestConfiguration.class})
class Boot4MetricsIntegrationTest {

    @Autowired
    private MeterRegistry meterRegistry;

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

    @Test
    void registersDiagnosticControllerSeparatelyFromMicrometerMetrics() {
        assertNotNull(diagnosticController);
        assertNotNull(meterRegistry);
    }

    @Test
    void publishesHibernateAndNearCacheMetersAndAutoRegistersRuntimeCaches() {
        Long entityId = transactionTemplate.execute(
                status -> repository.save(new SharedTestCachedEntity("boot4-metrics")).getId()
        );
        assertNotNull(entityId);

        transactionTemplate.execute(status -> repository.findById(entityId).orElseThrow());
        transactionTemplate.execute(status -> repository.findById(entityId).orElseThrow());

        assertTrue(!meterRegistry.find("hazelcast.toolkit.hibernate.l2.hit.count").meters().isEmpty());
        assertTrue(!meterRegistry.find("hazelcast.toolkit.hibernate.l2.miss.count").meters().isEmpty());

        Gauge regionGauge = waitForGauge("hazelcast.toolkit.near.cache.enabled", SharedTestCachedEntity.CACHE_REGION, "jcache");
        assertNotNull(regionGauge);
        assertTrue(regionGauge.value() >= 0.0d);

        String runtimeMapName = "boot4-runtime-map";
        hazelcastInstance.getMap(runtimeMapName).put("key", "value");
        Gauge runtimeMapGauge = waitForGauge("hazelcast.toolkit.near.cache.enabled", runtimeMapName, "imap");
        assertNotNull(runtimeMapGauge);

        String runtimeCacheName = "boot4-runtime-cache";
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
