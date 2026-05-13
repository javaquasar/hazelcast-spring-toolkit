package io.github.javaquasar.hazelcast.toolkit.springboot2.config;

import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.springboot2.actuator.HazelcastToolkitHealthIndicator;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(HazelcastToolkitAutoConfiguration.class)
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnBean(HazelcastInstance.class)
@ConditionalOnProperty(prefix = "hazelcast.toolkit.health", name = "enabled", havingValue = "true")
public class HazelcastHealthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "hazelcastToolkitHealthIndicator")
    public HazelcastToolkitHealthIndicator hazelcastToolkitHealthIndicator(HazelcastInstance hazelcastInstance) {
        return new HazelcastToolkitHealthIndicator(hazelcastInstance);
    }
}
