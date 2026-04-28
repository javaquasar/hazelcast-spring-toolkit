package io.github.javaquasar.hazelcast.toolkit.springboot4.config;

import com.hazelcast.core.HazelcastInstance;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
@AutoConfigureAfter(HazelcastToolkitAutoConfiguration.class)
@AutoConfigureBefore(CacheAutoConfiguration.class)
public class HazelcastNativeSpringCacheAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.hazelcast.spring.cache.HazelcastCacheManager")
    static class NativeSpringCacheConfiguration {

        @Bean
        @ConditionalOnMissingBean(org.springframework.cache.CacheManager.class)
        @ConditionalOnProperty(
                prefix = "hazelcast.toolkit.spring-cache",
                name = "mode",
                havingValue = "native"
        )
        public org.springframework.cache.CacheManager hazelcastSpringCacheManager(HazelcastInstance hazelcastInstance) {
            return new com.hazelcast.spring.cache.HazelcastCacheManager(hazelcastInstance);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingClass("com.hazelcast.spring.cache.HazelcastCacheManager")
    static class NativeSpringCacheMissingDependencyConfiguration {

        @Bean
        @ConditionalOnProperty(
                prefix = "hazelcast.toolkit.spring-cache",
                name = "mode",
                havingValue = "native"
        )
        public Object hazelcastSpringCacheNativeModeMissingDependencyFailure() {
            throw new IllegalStateException(
                    "hazelcast.toolkit.spring-cache.mode=native requires com.hazelcast:hazelcast-spring " +
                            "on the application classpath because it uses " +
                            "com.hazelcast.spring.cache.HazelcastCacheManager."
            );
        }
    }
}
