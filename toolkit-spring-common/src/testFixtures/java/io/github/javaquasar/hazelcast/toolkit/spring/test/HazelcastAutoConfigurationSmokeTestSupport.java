package io.github.javaquasar.hazelcast.toolkit.spring.test;

import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientFactory;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HazelcastClientProperties;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties;
import io.github.javaquasar.hazelcast.toolkit.spring.listener.HzListenersAutoRegistrar;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Proxy;

public abstract class HazelcastAutoConfigurationSmokeTestSupport {

    protected void assertRegistersSharedBeans(Class<?> autoConfigurationClass) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(TestConfig.class, autoConfigurationClass);
            context.refresh();

            requireBean(context, HazelcastInstance.class);
            requireBean(context, HazelcastClientFactory.class);
            requireBean(context, HazelcastClientProperties.class);
            requireBean(context, HzToolkitProperties.class);
            requireBean(context, HzListenersAutoRegistrar.class);
        }
    }

    private static <T> void requireBean(AnnotationConfigApplicationContext context, Class<T> beanType) {
        if (context.getBean(beanType) == null) {
            throw new IllegalStateException("Expected Spring bean was not registered: " + beanType.getName());
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
