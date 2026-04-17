package io.github.javaquasar.hazelcast.toolkit.boot3;

import io.github.javaquasar.hazelcast.toolkit.boot3.l2.L2CacheTestConfiguration;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties.Hibernate.L2.RegionFactoryType;
import io.github.javaquasar.hazelcast.toolkit.spring.test.boot.SharedTestApplication;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntity;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntityRepository;
import io.github.javaquasar.hazelcast.toolkit.testcontainers.TestcontainersEnvironment;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.cache.Cache;
import javax.cache.CacheManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class Boot3HibernateL2PerformanceComparisonTest extends TestcontainersEnvironment {

    private static final int WARM_READ_ITERATIONS = 8;
    private static final List<Measurement> RECORDED_MEASUREMENTS = new ArrayList<>();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        TestcontainersEnvironment.registerSpringProperties(registry);
    }

    @Test
    @DisplayName("should characterize Boot 3 warm-read performance for JCache and native Hazelcast with and without near cache")
    void shouldCharacterizeWarmReadPerformanceForJCacheAndNativeHazelcast() {
        Measurement jcache = runScenario(RegionFactoryType.JCACHE, true, false, "boot3-perf-jcache");
        Measurement jcacheNearCache = runScenario(RegionFactoryType.JCACHE, true, true, "boot3-perf-jcache-nearcache");
        Measurement nativeLocal = runScenario(RegionFactoryType.HAZELCAST_LOCAL, true, false, "boot3-perf-native");
        Measurement nativeLocalNearCache = runScenario(RegionFactoryType.HAZELCAST_LOCAL, true, true, "boot3-perf-native-nearcache");

        assertAll(
                () -> assertWarmReadsBeatColdRead(jcache),
                () -> assertWarmReadsBeatColdRead(jcacheNearCache),
                () -> assertWarmReadsBeatColdRead(nativeLocal),
                () -> assertWarmReadsBeatColdRead(nativeLocalNearCache),
                () -> assertTrue(
                        nativeLocal.averageWarmReadNanos() <= jcache.averageWarmReadNanos() * 3,
                        () -> "Expected native Hazelcast warm reads to stay within a broad 3x envelope of JCache warm reads, "
                                + "but got native=" + nativeLocal.averageWarmReadNanos() + "ns and jcache="
                                + jcache.averageWarmReadNanos() + "ns"
                ),
                () -> assertTrue(
                        jcacheNearCache.averageWarmReadNanos() <= jcache.averageWarmReadNanos() * 3,
                        () -> "Expected JCache near-cache warm reads to stay within a broad 3x envelope of JCache baseline, "
                                + "but got nearCache=" + jcacheNearCache.averageWarmReadNanos() + "ns and baseline="
                                + jcache.averageWarmReadNanos() + "ns"
                ),
                () -> assertTrue(
                        nativeLocalNearCache.averageWarmReadNanos() <= nativeLocal.averageWarmReadNanos() * 5,
                        () -> "Expected native Hazelcast near-cache warm reads to stay within a broad 5x envelope of native baseline, "
                                + "but got nearCache=" + nativeLocalNearCache.averageWarmReadNanos() + "ns and baseline="
                                + nativeLocal.averageWarmReadNanos() + "ns"
                )
        );
    }

    @AfterAll
    static void printMeasurements() {
        for (Measurement measurement : RECORDED_MEASUREMENTS) {
            System.out.println(
                    "PERF_RESULT_BOOT3 mode=" + measurement.regionFactoryType()
                            + " nearCacheEnabled=" + measurement.nearCacheEnabled()
                            + " coldReadNanos=" + measurement.coldReadNanos()
                            + " averageWarmReadNanos=" + measurement.averageWarmReadNanos()
                            + " l2HitsDuringMeasuredReads=" + measurement.l2HitsDuringMeasuredReads()
            );
        }
    }

    private Measurement runScenario(
            RegionFactoryType regionFactoryType,
            boolean extendedConfig,
            boolean nearCacheEnabled,
            String instanceName) {

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(SharedTestApplication.class, L2CacheTestConfiguration.class)
                .properties(baseProperties(regionFactoryType, extendedConfig, nearCacheEnabled, instanceName))
                .run()) {

            SharedTestCachedEntityRepository repository = context.getBean(SharedTestCachedEntityRepository.class);
            TransactionTemplate transactionTemplate = context.getBean(TransactionTemplate.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            CacheManager cacheManager = context.getBean(CacheManager.class);
            EntityManagerFactory entityManagerFactory = context.getBean(EntityManagerFactory.class);

            Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
            statistics.clear();

            Cache<Object, Object> l2Cache = cacheManager.getCache(SharedTestCachedEntity.CACHE_REGION);
            if (l2Cache != null) {
                l2Cache.clear();
            }
            entityManagerFactory.getCache().evictAll();
            statistics.clear();

            Long entityId = transactionTemplate.execute(status -> repository.save(new SharedTestCachedEntity("perf-" + regionFactoryType.name())).getId());
            assertEquals(
                    1,
                    jdbcTemplate.queryForObject("select count(*) from test_cached_entities where id = ?", Integer.class, entityId)
            );

            if (l2Cache != null) {
                l2Cache.clear();
            }
            entityManagerFactory.getCache().evictAll();
            statistics.clear();

            long hitCountBeforeMeasuredReads = statistics.getSecondLevelCacheHitCount();

            long coldReadNanos = measureSingleRead(transactionTemplate, repository, entityId);
            long warmReadTotalNanos = 0L;
            for (int index = 0; index < WARM_READ_ITERATIONS; index++) {
                warmReadTotalNanos += measureSingleRead(transactionTemplate, repository, entityId);
            }

            long hitsDuringMeasuredReads = statistics.getSecondLevelCacheHitCount() - hitCountBeforeMeasuredReads;

            Measurement measurement = new Measurement(
                    regionFactoryType,
                    nearCacheEnabled,
                    coldReadNanos,
                    warmReadTotalNanos / WARM_READ_ITERATIONS,
                    hitsDuringMeasuredReads
            );
            RECORDED_MEASUREMENTS.add(measurement);
            return measurement;
        }
    }

    private long measureSingleRead(
            TransactionTemplate transactionTemplate,
            SharedTestCachedEntityRepository repository,
            Long entityId) {

        long startedAt = System.nanoTime();
        SharedTestCachedEntity entity = transactionTemplate.execute(status -> repository.findById(entityId).orElseThrow());
        long finishedAt = System.nanoTime();
        assertEquals(entityId, entity.getId());
        return finishedAt - startedAt;
    }

    private void assertWarmReadsBeatColdRead(Measurement measurement) {
        assertAll(
                () -> assertTrue(
                        measurement.l2HitsDuringMeasuredReads() >= WARM_READ_ITERATIONS,
                        () -> "Expected Hibernate L2 hits during measured reads for " + measurement.regionFactoryType()
                                + ", but got " + measurement.l2HitsDuringMeasuredReads()
                ),
                () -> assertTrue(
                        measurement.averageWarmReadNanos() < measurement.coldReadNanos(),
                        () -> "Expected warm reads to be faster than the cold measured read for "
                                + measurement.regionFactoryType()
                                + ", but cold=" + measurement.coldReadNanos() + "ns and warm="
                                + measurement.averageWarmReadNanos() + "ns"
                )
        );
    }

    private Map<String, Object> baseProperties(
            RegionFactoryType regionFactoryType,
            boolean extendedConfig,
            boolean nearCacheEnabled,
            String instanceName) {

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.main.web-application-type", "none");
        properties.put("spring.datasource.url", jdbcUrl());
        properties.put("spring.datasource.username", dbUsername());
        properties.put("spring.datasource.password", dbPassword());
        properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        properties.put("spring.jpa.hibernate.ddl-auto", "create");
        properties.put("spring.jpa.open-in-view", "false");
        properties.put("hazelcast.toolkit.hibernate.l2.enabled", "true");
        properties.put("hazelcast.toolkit.hibernate.l2.extended-config", Boolean.toString(extendedConfig));
        properties.put("hazelcast.toolkit.hibernate.l2.region-factory", regionFactoryType.name());
        properties.put("hazelcast.toolkit.hibernate.l2.use-statistics", "true");
        properties.put("test.hazelcast.near-cache.enabled", Boolean.toString(nearCacheEnabled));
        properties.put("hazelcast.client.instance-name", instanceName + "-" + UUID.randomUUID());
        properties.put("hazelcast.client.cluster-name", hazelcastClusterName());
        properties.put("hazelcast.client.network.cluster-members", hazelcastMembers());
        properties.put("hazelcast.client.network.smart-routing", "false");
        return properties;
    }

    private record Measurement(
            RegionFactoryType regionFactoryType,
            boolean nearCacheEnabled,
            long coldReadNanos,
            long averageWarmReadNanos,
            long l2HitsDuringMeasuredReads) {
    }
}
