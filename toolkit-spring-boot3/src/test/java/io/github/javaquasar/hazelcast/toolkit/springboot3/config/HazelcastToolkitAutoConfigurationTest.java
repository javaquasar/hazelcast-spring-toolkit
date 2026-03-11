package io.github.javaquasar.hazelcast.toolkit.springboot3.config;

import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientFactory;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HazelcastClientProperties;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties;
import io.github.javaquasar.hazelcast.toolkit.spring.listener.HzListenersAutoRegistrar;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class HazelcastToolkitAutoConfigurationTest {

    @Test
    void registersSharedBeans() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(TestConfig.class, HazelcastToolkitAutoConfiguration.class);
            context.refresh();

            assertNotNull(context.getBean(HazelcastInstance.class));
            assertNotNull(context.getBean(HazelcastClientFactory.class));
            assertNotNull(context.getBean(HazelcastClientProperties.class));
            assertNotNull(context.getBean(HzToolkitProperties.class));
            assertNotNull(context.getBean(HzListenersAutoRegistrar.class));
        }
    }

    @Configuration
    static class TestConfig {

        @Bean
        HazelcastInstance hazelcastInstance() {
            return (HazelcastInstance) Proxy.newProxyInstance(
                    HazelcastInstance.class.getClassLoader(),
                    new Class<?>[]{HazelcastInstance.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("toString")) {
                            return "testHazelcastInstance";
                        }
                        if (method.getReturnType().equals(boolean.class)) {
                            return false;
                        }
                        if (method.getReturnType().equals(int.class)) {
                            return 0;
                        }
                        if (method.getReturnType().equals(long.class)) {
                            return 0L;
                        }
                        return null;
                    }
            );
        }
    }
}
