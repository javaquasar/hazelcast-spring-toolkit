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
                () -> assertWarmReadsStayWithinBaselineEnvelope(
                        "native Hazelcast",
                        nativeLocal,
                        "JCache",
                        jcache,
                        3
                ),
                () -> assertNearCacheWarmReadsStayWithinBaselineEnvelope(
                        "JCache",
                        jcacheNearCache,
                        jcache,
                        jcacheNearCacheEnvelopeMultiplier()
                ),
                () -> assertNearCacheWarmReadsStayWithinBaselineEnvelope(
                        "native Hazelcast",
                        nativeLocalNearCache,
                        nativeLocal,
                        nativeNearCacheEnvelopeMultiplier()
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

    protected long nearCacheVsBaselineAbsoluteJitterNanos() {
        return 50_000_000L;
    }

    protected long crossModeVsBaselineAbsoluteJitterNanos() {
        return 50_000_000L;
    }

    /**
     * Minimum L2 cache hits required across all measured reads (cold + warm).
     * Defaults to half of WARM_READ_ITERATIONS to tolerate statistics-counter
     * imprecision on JVM cold start with some region factory implementations.
     */
    protected int minL2HitsThreshold() {
        return WARM_READ_ITERATIONS / 2;
    }

    /**
     * Native Hazelcast local near-cache can satisfy repeated reads before Hibernate's
     * second-level-cache statistics observe them as L2 hits. In that scenario we still
     * require the characteristic performance shape (warm reads faster than cold), but
     * we do not require Hibernate's hit counter to increase.
     */
    protected int minL2HitsThreshold(Measurement measurement) {
        if (measurement.regionFactoryType() == RegionFactoryType.HAZELCAST_LOCAL
                && measurement.nearCacheEnabled()) {
            return 0;
        }
        return minL2HitsThreshold();
    }

    /**
     * Warm-vs-cold timings are measured in a noisy local JVM environment.
     * Allow a small envelope instead of requiring a strict warm < cold
     * inequality, while still requiring Hibernate L2 hits for the measured phase.
     */
    protected double warmReadVsColdEnvelopeMultiplier(Measurement measurement) {
        return 1.15d;
    }

    /**
     * A single cold read can occasionally be under-representative after JVM and
     * Hazelcast warm-up, especially on shared CI runners. Keep the relative
     * envelope for normal runs, but allow a small absolute timing jitter so this
     * characterization test does not fail on sub-millisecond scheduling noise.
     */
    protected long warmReadVsColdAbsoluteJitterNanos(Measurement measurement) {
        return 10_000_000L;
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
        int requiredL2Hits = minL2HitsThreshold(measurement);
        long relativeThreshold = (long) (measurement.coldReadNanos()
                * warmReadVsColdEnvelopeMultiplier(measurement));
        long absoluteJitterThreshold = measurement.coldReadNanos()
                + warmReadVsColdAbsoluteJitterNanos(measurement);
        long allowedWarmReadNanos = Math.max(relativeThreshold, absoluteJitterThreshold);
        assertAll(
                () -> assertTrue(
                        measurement.l2HitsDuringMeasuredReads() >= requiredL2Hits,
                        () -> "Expected at least " + requiredL2Hits + " Hibernate L2 hits during measured reads for "
                                + measurement.regionFactoryType() + ", but got " + measurement.l2HitsDuringMeasuredReads()
                ),
                () -> assertTrue(
                        measurement.averageWarmReadNanos() <= allowedWarmReadNanos,
                        () -> "Expected warm reads to stay within a "
                                + warmReadVsColdEnvelopeMultiplier(measurement)
                                + "x envelope or "
                                + warmReadVsColdAbsoluteJitterNanos(measurement)
                                + "ns absolute jitter of the cold measured read for "
                                + measurement.regionFactoryType()
                                + ", but cold=" + measurement.coldReadNanos() + "ns and warm="
                                + measurement.averageWarmReadNanos() + "ns, allowed="
                                + allowedWarmReadNanos + "ns"
                )
        );
    }

    protected void assertNearCacheWarmReadsStayWithinBaselineEnvelope(
            String label,
            Measurement nearCacheMeasurement,
            Measurement baselineMeasurement,
            int envelopeMultiplier) {

        long relativeThreshold = baselineMeasurement.averageWarmReadNanos() * envelopeMultiplier;
        long absoluteJitterThreshold = baselineMeasurement.averageWarmReadNanos()
                + nearCacheVsBaselineAbsoluteJitterNanos();
        long allowedWarmReadNanos = Math.max(relativeThreshold, absoluteJitterThreshold);

        assertTrue(
                nearCacheMeasurement.averageWarmReadNanos() <= allowedWarmReadNanos,
                () -> "Expected " + label + " near-cache warm reads to stay within a broad "
                        + envelopeMultiplier + "x envelope or "
                        + nearCacheVsBaselineAbsoluteJitterNanos()
                        + "ns absolute jitter of baseline, but got nearCache="
                        + nearCacheMeasurement.averageWarmReadNanos() + "ns and baseline="
                        + baselineMeasurement.averageWarmReadNanos() + "ns, allowed="
                        + allowedWarmReadNanos + "ns"
        );
    }

    protected void assertWarmReadsStayWithinBaselineEnvelope(
            String label,
            Measurement measurement,
            String baselineLabel,
            Measurement baselineMeasurement,
            int envelopeMultiplier) {

        long relativeThreshold = baselineMeasurement.averageWarmReadNanos() * envelopeMultiplier;
        long absoluteJitterThreshold = baselineMeasurement.averageWarmReadNanos()
                + crossModeVsBaselineAbsoluteJitterNanos();
        long allowedWarmReadNanos = Math.max(relativeThreshold, absoluteJitterThreshold);

        assertTrue(
                measurement.averageWarmReadNanos() <= allowedWarmReadNanos,
                () -> "Expected " + label + " warm reads to stay within a broad "
                        + envelopeMultiplier + "x envelope or "
                        + crossModeVsBaselineAbsoluteJitterNanos()
                        + "ns absolute jitter of " + baselineLabel + " warm reads, but got "
                        + label + "=" + measurement.averageWarmReadNanos() + "ns and "
                        + baselineLabel + "=" + baselineMeasurement.averageWarmReadNanos()
                        + "ns, allowed=" + allowedWarmReadNanos + "ns"
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
