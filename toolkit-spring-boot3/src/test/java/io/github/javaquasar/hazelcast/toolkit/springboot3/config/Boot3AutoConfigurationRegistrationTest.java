package io.github.javaquasar.hazelcast.toolkit.springboot3.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Boot3AutoConfigurationRegistrationTest {

    @Test
    void autoConfigurationImportsRegistersAllBoot3AutoConfigurations() throws IOException {
        List<String> registeredConfigurations = readAutoConfigurationImports();

        assertThat(registeredConfigurations).containsExactly(
                HazelcastToolkitAutoConfiguration.class.getName(),
                HazelcastJCacheAutoConfiguration.class.getName(),
                HazelcastNativeSpringCacheAutoConfiguration.class.getName(),
                HazelcastHibernateL2AutoConfiguration.class.getName(),
                HazelcastHealthAutoConfiguration.class.getName(),
                HazelcastActuatorAutoConfiguration.class.getName()
        );
    }

    private List<String> readAutoConfigurationImports() throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(stream)
                    .as("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
                    .isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .filter(line -> !line.startsWith("#"))
                    .toList();
        }
    }
}
