package io.github.javaquasar.hazelcast.toolkit.boot4;

import io.github.javaquasar.hazelcast.toolkit.boot4.l2.Boot4L2CacheTestConfiguration;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties.Hibernate.L2.RegionFactoryType;
import io.github.javaquasar.hazelcast.toolkit.spring.test.boot.SharedTestApplication;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.AbstractHibernateL2PerformanceComparisonSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.Map;

/**
 * Performance characterisation test verifying that Boot 4 L2 cache warm reads outperform
 * cold reads for all four region-factory / near-cache combinations.
 *
 * <p>Mirrors the Boot 2 structure: uses an H2 in-memory database and an in-process Hazelcast
 * member via {@link Boot4L2CacheTestConfiguration}. No Testcontainers required.
 *
 * <p>The {@code nativeNearCacheEnvelopeMultiplier} is kept at {@code 3} (same as Boot 2)
 * because the test runs against a local embedded member — no network overhead from Docker.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Boot4HibernateL2PerformanceComparisonTest extends AbstractHibernateL2PerformanceComparisonSupport {

    @Test
    @DisplayName("should characterize Boot 4 warm-read performance for JCache and native Hazelcast with and without near cache")
    void shouldCharacterizeWarmReadPerformanceForJCacheAndNativeHazelcast() {
        runStandardScenariosAndAssert();
    }

    @AfterAll
    void printMeasurements() {
        printRecordedMeasurements("PERF_RESULT_BOOT4");
    }

    @Override
    protected String scenarioPrefix() {
        return "boot4-perf";
    }

    @Override
    protected SpringApplicationBuilder createApplicationBuilder() {
        return new SpringApplicationBuilder(SharedTestApplication.class, Boot4L2CacheTestConfiguration.class);
    }

    @Override
    protected void addScenarioSpecificProperties(
            Map<String, Object> properties,
            RegionFactoryType regionFactoryType,
            boolean extendedConfig,
            boolean nearCacheEnabled,
            String instanceName) {
        addJdbcAndHazelcastScenarioProperties(
                properties,
                "jdbc:h2:mem:" + instanceName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "org.h2.Driver",
                "sa",
                "",
                "org.hibernate.dialect.H2Dialect",
                "create-drop",
                Boot4L2CacheTestConfiguration.CLUSTER_NAME,
                "hazelcast.client.network.cluster-members[0]",
                Boot4L2CacheTestConfiguration.MEMBER_ADDRESS
        );
    }
}
