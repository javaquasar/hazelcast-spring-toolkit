package io.github.javaquasar.hazelcast.toolkit.spring.test.l2;

import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties.Hibernate.L2.RegionFactoryType;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import javax.cache.Cache;
import javax.cache.CacheManager;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractHibernateL2PerformanceComparisonSupport {

    protected static final int WARM_READ_ITERATIONS = 8;
    private final List<Measurement> recordedMeasurements = new ArrayList<>();

    protected final void runStandardScenariosAndAssert() {
        Measurement jcache = runScenario(RegionFactoryType.JCACHE, true, false, scenarioPrefix() + "-jcache");
        Measurement jcacheNearCache = runScenario(RegionFactoryType.JCACHE, true, true, scenarioPrefix() + "-jcache-nearcache");
        Measurement nativeLocal = runScenario(RegionFactoryType.HAZELCAST_LOCAL, true, false, scenarioPrefix() + "-native");
        Measurement nativeLocalNearCache = runScenario(RegionFactoryType.HAZELCAST_LOCAL, true, true, scenarioPrefix() + "-native-nearcache");

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
                        jcacheNearCache.averageWarmReadNanos() <= jcache.averageWarmReadNanos() * jcacheNearCacheEnvelopeMultiplier(),
                        () -> "Expected JCache near-cache warm reads to stay within a broad "
                                + jcacheNearCacheEnvelopeMultiplier() + "x envelope of JCache baseline, "
                                + "but got nearCache=" + jcacheNearCache.averageWarmReadNanos() + "ns and baseline="
                                + jcache.averageWarmReadNanos() + "ns"
                ),
                () -> assertTrue(
                        nativeLocalNearCache.averageWarmReadNanos() <= nativeLocal.averageWarmReadNanos() * nativeNearCacheEnvelopeMultiplier(),
                        () -> "Expected native Hazelcast near-cache warm reads to stay within a broad "
                                + nativeNearCacheEnvelopeMultiplier() + "x envelope of native baseline, "
                                + "but got nearCache=" + nativeLocalNearCache.averageWarmReadNanos() + "ns and baseline="
                                + nativeLocal.averageWarmReadNanos() + "ns"
                )
        );
    }

    protected Measurement runScenario(
            RegionFactoryType regionFactoryType,
            boolean extendedConfig,
            boolean nearCacheEnabled,
            String instanceName) {

        try (ConfigurableApplicationContext context = createContext(regionFactoryType, extendedConfig, nearCacheEnabled, instanceName)) {
            SharedTestCachedEntityRepository repository = context.getBean(SharedTestCachedEntityRepository.class);
            TransactionTemplate transactionTemplate = context.getBean(TransactionTemplate.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            CacheManager cacheManager = context.getBean(CacheManager.class);

            Statistics statistics = extractStatistics(context);
            statistics.clear();

            Cache<Object, Object> l2Cache = cacheManager.getCache(SharedTestCachedEntity.CACHE_REGION);
            if (l2Cache != null) {
                l2Cache.clear();
            }
            evictAllEntityManagerCaches(context);
            statistics.clear();

            Long entityId = transactionTemplate.execute(status -> repository.save(new SharedTestCachedEntity("perf-" + regionFactoryType.name())).getId());
            assertEquals(
                    1,
                    jdbcTemplate.queryForObject("select count(*) from test_cached_entities where id = ?", Integer.class, entityId)
            );

            if (l2Cache != null) {
                l2Cache.clear();
            }
            evictAllEntityManagerCaches(context);
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
            recordedMeasurements.add(measurement);
            return measurement;
        }
    }

    protected void printRecordedMeasurements(String prefix) {
        for (Measurement measurement : recordedMeasurements) {
            System.out.println(
                    prefix + " mode=" + measurement.regionFactoryType()
                            + " nearCacheEnabled=" + measurement.nearCacheEnabled()
                            + " coldReadNanos=" + measurement.coldReadNanos()
                            + " averageWarmReadNanos=" + measurement.averageWarmReadNanos()
                            + " l2HitsDuringMeasuredReads=" + measurement.l2HitsDuringMeasuredReads()
            );
        }
    }

    protected int nativeNearCacheEnvelopeMultiplier() {
        return 5;
    }

    protected int jcacheNearCacheEnvelopeMultiplier() {
        return 5;
    }

    /**
     * Minimum L2 cache hits required across all measured reads (cold + warm).
     * Defaults to half of WARM_READ_ITERATIONS to tolerate statistics-counter
     * imprecision on JVM cold start with some region factory implementations.
     */
    protected int minL2HitsThreshold() {
        return WARM_READ_ITERATIONS / 2;
    }

    protected abstract String scenarioPrefix();

    protected final ConfigurableApplicationContext createContext(
            RegionFactoryType regionFactoryType,
            boolean extendedConfig,
            boolean nearCacheEnabled,
            String instanceName) {
        return createApplicationBuilder()
                .properties(createScenarioProperties(regionFactoryType, extendedConfig, nearCacheEnabled, instanceName))
                .run();
    }

    protected abstract SpringApplicationBuilder createApplicationBuilder();

    protected final Map<String, Object> createScenarioProperties(
            RegionFactoryType regionFactoryType,
            boolean extendedConfig,
            boolean nearCacheEnabled,
            String instanceName) {

        Map<String, Object> properties = createBaseL2PerformanceProperties(
                regionFactoryType,
                extendedConfig,
                nearCacheEnabled,
                instanceName
        );
        addScenarioSpecificProperties(properties, regionFactoryType, extendedConfig, nearCacheEnabled, instanceName);
        return properties;
    }

    protected Map<String, Object> createBaseL2PerformanceProperties(
            RegionFactoryType regionFactoryType,
            boolean extendedConfig,
            boolean nearCacheEnabled,
            String instanceName) {

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.main.web-application-type", "none");
        properties.put("spring.jpa.open-in-view", "false");
        properties.put("hazelcast.toolkit.hibernate.l2.enabled", "true");
        properties.put("hazelcast.toolkit.hibernate.l2.extended-config", Boolean.toString(extendedConfig));
        properties.put("hazelcast.toolkit.hibernate.l2.region-factory", regionFactoryType.name());
        properties.put("hazelcast.toolkit.hibernate.l2.use-statistics", "true");
        properties.put("test.hazelcast.near-cache.enabled", Boolean.toString(nearCacheEnabled));
        properties.put("hazelcast.client.instance-name", uniqueClientInstanceName(instanceName));
        properties.put("hazelcast.client.network.smart-routing", "false");
        return properties;
    }

    protected abstract void addScenarioSpecificProperties(
            Map<String, Object> properties,
            RegionFactoryType regionFactoryType,
            boolean extendedConfig,
            boolean nearCacheEnabled,
            String instanceName);

    protected final void addJdbcAndHazelcastScenarioProperties(
            Map<String, Object> properties,
            String datasourceUrl,
            String datasourceDriverClassName,
            String datasourceUsername,
            String datasourcePassword,
            String databasePlatform,
            String ddlAuto,
            String clusterName,
            String clusterMembersPropertyKey,
            Object clusterMembersValue) {

        properties.put("spring.datasource.url", datasourceUrl);
        properties.put("spring.datasource.driver-class-name", datasourceDriverClassName);
        properties.put("spring.datasource.username", datasourceUsername);
        properties.put("spring.datasource.password", datasourcePassword);
        if (databasePlatform != null) {
            properties.put("spring.jpa.database-platform", databasePlatform);
        }
        properties.put("spring.jpa.hibernate.ddl-auto", ddlAuto);
        properties.put("hazelcast.client.cluster-name", clusterName);
        properties.put(clusterMembersPropertyKey, clusterMembersValue);
    }

    protected void assertWarmReadsBeatColdRead(Measurement measurement) {
        assertAll(
                () -> assertTrue(
                        measurement.l2HitsDuringMeasuredReads() >= minL2HitsThreshold(),
                        () -> "Expected at least " + minL2HitsThreshold() + " Hibernate L2 hits during measured reads for "
                                + measurement.regionFactoryType() + ", but got " + measurement.l2HitsDuringMeasuredReads()
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

    private Statistics extractStatistics(ConfigurableApplicationContext context) {
        Object entityManagerFactory = context.getBean("entityManagerFactory");
        try {
            Method unwrap = entityManagerFactory.getClass().getMethod("unwrap", Class.class);
            SessionFactory sessionFactory = (SessionFactory) unwrap.invoke(entityManagerFactory, SessionFactory.class);
            return sessionFactory.getStatistics();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to unwrap Hibernate SessionFactory from entityManagerFactory", ex);
        }
    }

    private void evictAllEntityManagerCaches(ConfigurableApplicationContext context) {
        Object entityManagerFactory = context.getBean("entityManagerFactory");
        try {
            Object cache = entityManagerFactory.getClass().getMethod("getCache").invoke(entityManagerFactory);
            cache.getClass().getMethod("evictAll").invoke(cache);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to evict all JPA entity manager caches", ex);
        }
    }

    private String uniqueClientInstanceName(String instanceName) {
        return instanceName + "-" + UUID.randomUUID();
    }

    protected record Measurement(
            RegionFactoryType regionFactoryType,
            boolean nearCacheEnabled,
            long coldReadNanos,
            long averageWarmReadNanos,
            long l2HitsDuringMeasuredReads) {
    }
}
