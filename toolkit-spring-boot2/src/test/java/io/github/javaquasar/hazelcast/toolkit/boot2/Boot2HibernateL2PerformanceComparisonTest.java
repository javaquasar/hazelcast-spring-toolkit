package io.github.javaquasar.hazelcast.toolkit.boot2;

import io.github.javaquasar.hazelcast.toolkit.boot2.l2.Boot2L2CacheTestConfiguration;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties.Hibernate.L2.RegionFactoryType;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.AbstractHibernateL2PerformanceComparisonSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.Map;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Boot2HibernateL2PerformanceComparisonTest extends AbstractHibernateL2PerformanceComparisonSupport {

    @Test
    @DisplayName("should characterize Boot 2 warm-read performance for JCache and native Hazelcast with and without near cache")
    void shouldCharacterizeWarmReadPerformanceForJCacheAndNativeHazelcast() {
        runStandardScenariosAndAssert();
    }

    @AfterAll
    void printMeasurements() {
        printRecordedMeasurements("PERF_RESULT");
    }

    @Override
    protected String scenarioPrefix() {
        return "boot2-perf";
    }

    @Override
    protected SpringApplicationBuilder createApplicationBuilder() {
        return new SpringApplicationBuilder(Boot2TestApplication.class, Boot2L2CacheTestConfiguration.class);
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
                Boot2L2CacheTestConfiguration.CLUSTER_NAME,
                "hazelcast.client.network.cluster-members[0]",
                Boot2L2CacheTestConfiguration.MEMBER_ADDRESS
        );
    }
}
