package io.github.javaquasar.hazelcast.toolkit.boot4;

import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.spring.test.MemberToClientMigrationTestSupport;
import io.github.javaquasar.hazelcast.toolkit.springboot4.config.HazelcastToolkitAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class Boot4MemberToClientMigrationIntegrationTest extends MemberToClientMigrationTestSupport {

    @Test
    void applicationRestartsAsClientWhileClusterAndDataRemainAvailable() {
        assertMemberToClientMigration((properties, verification) -> contextRunner()
                .withPropertyValues(properties)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    verification.accept(context.getBean(HazelcastInstance.class));
                }));
    }

    private static ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        HazelcastToolkitAutoConfiguration.class
                ));
    }
}
