package io.github.javaquasar.hazelcast.toolkit.boot3;

import io.github.javaquasar.hazelcast.toolkit.boot3.l2.L2CacheTestConfiguration;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties.Hibernate.L2.RegionFactoryType;
import io.github.javaquasar.hazelcast.toolkit.spring.test.boot.SharedTestApplication;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.AbstractHibernateL2PerformanceComparisonSupport;
import io.github.javaquasar.hazelcast.toolkit.testcontainers.TestcontainersEnvironment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Boot3HibernateL2PerformanceComparisonTest extends TestcontainersEnvironment {

    private final Boot3Support support = new Boot3Support();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        TestcontainersEnvironment.registerSpringProperties(registry);
    }

    @Test
    @DisplayName("should characterize Boot 3 warm-read performance for JCache and native Hazelcast with and without near cache")
    void shouldCharacterizeWarmReadPerformanceForJCacheAndNativeHazelcast() {
        support.runScenarios();
    }

    @AfterAll
    void printMeasurements() {
        support.printMeasurements();
    }

    private class Boot3Support extends AbstractHibernateL2PerformanceComparisonSupport {

        void runScenarios() {
            runStandardScenariosAndAssert();
        }

        void printMeasurements() {
            printRecordedMeasurements("PERF_RESULT_BOOT3");
        }

        @Override
        protected String scenarioPrefix() {
            return "boot3-perf";
        }

        @Override
        protected int nativeNearCacheEnvelopeMultiplier() {
            return 5;
        }

        @Override
        protected SpringApplicationBuilder createApplicationBuilder() {
            return new SpringApplicationBuilder(SharedTestApplication.class, L2CacheTestConfiguration.class);
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
                    jdbcUrl(),
                    "org.postgresql.Driver",
                    dbUsername(),
                    dbPassword(),
                    null,
                    "create",
                    hazelcastClusterName(),
                    "hazelcast.client.network.cluster-members",
                    hazelcastMembers()
            );
        }
    }
}
