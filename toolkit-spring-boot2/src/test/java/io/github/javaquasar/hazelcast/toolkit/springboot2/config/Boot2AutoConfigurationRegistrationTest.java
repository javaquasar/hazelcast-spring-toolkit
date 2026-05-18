package io.github.javaquasar.hazelcast.toolkit.springboot2.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class Boot2AutoConfigurationRegistrationTest {

    @Test
    void springFactoriesRegistersAllBoot2AutoConfigurations() throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("META-INF/spring.factories")) {
            assertThat(stream).as("META-INF/spring.factories").isNotNull();
            properties.load(stream);
        }

        String value = properties.getProperty("org.springframework.boot.autoconfigure.EnableAutoConfiguration");
        assertThat(value).isNotBlank();

        Set<String> registeredConfigurations = new LinkedHashSet<>(Arrays.asList(value.split("\\s*,\\s*")));
        assertThat(registeredConfigurations).containsExactly(
                HazelcastToolkitAutoConfiguration.class.getName(),
                HazelcastJCacheAutoConfiguration.class.getName(),
                HazelcastNativeSpringCacheAutoConfiguration.class.getName(),
                HazelcastHibernateL2AutoConfiguration.class.getName(),
                HazelcastHealthAutoConfiguration.class.getName(),
                HazelcastActuatorAutoConfiguration.class.getName()
        );
    }
}
