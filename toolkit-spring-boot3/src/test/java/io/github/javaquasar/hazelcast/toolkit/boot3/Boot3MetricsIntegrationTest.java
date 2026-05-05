package io.github.javaquasar.hazelcast.toolkit.boot3;

import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.boot3.l2.L2CacheTestConfiguration;
import io.github.javaquasar.hazelcast.toolkit.metrics.spring.HzToolkitMetricsController;
import io.github.javaquasar.hazelcast.toolkit.spring.test.boot.SharedTestApplication;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntity;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntityRepository;
import io.github.javaquasar.hazelcast.toolkit.testcontainers.TestcontainersEnvironment;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = SharedTestApplication.class,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create",
                "spring.jpa.open-in-view=false",
                "hazelcast.toolkit.hibernate.l2.enabled=true",
                "hazelcast.toolkit.hibernate.l2.extended-config=true",
                "hazelcast.toolkit.hibernate.l2.use-statistics=true",
                "hazelcast.toolkit.metrics.enabled=true",
                "hazelcast.toolkit.metrics.diagnostic-endpoint.enabled=true",
                "test.hazelcast.near-cache.enabled=true",
                "hazelcast.client.instance-name=boot3-metrics-test"
        }
)
@Import({L2CacheTestConfiguration.class, Boot3MetricsIntegrationTest.MetricsTestConfiguration.class})
class Boot3MetricsIntegrationTest extends TestcontainersEnvironment {

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        TestcontainersEnvironment.registerSpringProperties(registry);
    }

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
    void publishesHibernateAndNearCacheMetersAndAutoRegistersRuntimeCaches() {
        Long entityId = transactionTemplate.execute(
                status -> repository.save(new SharedTestCachedEntity("boot3-metrics")).getId()
        );
        assertNotNull(entityId);

        transactionTemplate.execute(status -> repository.findById(entityId).orElseThrow());
        transactionTemplate.execute(status -> repository.findById(entityId).orElseThrow());

        assertTrue(!meterRegistry.find("hazelcast.toolkit.hibernate.l2.hit.count").meters().isEmpty());
        assertTrue(!meterRegistry.find("hazelcast.toolkit.hibernate.l2.miss.count").meters().isEmpty());

        Gauge regionGauge = waitForGauge("hazelcast.toolkit.near.cache.enabled", SharedTestCachedEntity.CACHE_REGION, "jcache");
        assertNotNull(regionGauge);
        assertTrue(regionGauge.value() >= 0.0d);

        String runtimeMapName = "boot3-runtime-map";
        hazelcastInstance.getMap(runtimeMapName).put("key", "value");
        Gauge runtimeMapGauge = waitForGauge("hazelcast.toolkit.near.cache.enabled", runtimeMapName, "imap");
        assertNotNull(runtimeMapGauge);

        String runtimeCacheName = "boot3-runtime-cache";
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
