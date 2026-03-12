package io.github.javaquasar.hazelcast.toolkit.springboot2.config;

import io.github.javaquasar.hazelcast.toolkit.spring.test.HazelcastAutoConfigurationSmokeTestSupport;
import org.junit.jupiter.api.Test;

class HazelcastToolkitAutoConfigurationTest extends HazelcastAutoConfigurationSmokeTestSupport {

    @Test
    void registersSharedBeans() {
        assertRegistersSharedBeans(HazelcastToolkitAutoConfiguration.class);
    }
}
