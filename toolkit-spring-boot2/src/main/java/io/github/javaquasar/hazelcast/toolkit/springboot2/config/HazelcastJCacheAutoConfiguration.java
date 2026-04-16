package io.github.javaquasar.hazelcast.toolkit.springboot2.config;

import com.hazelcast.cache.HazelcastCachingProvider;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.cache.CacheManager;
import javax.cache.spi.CachingProvider;
import java.util.Properties;

@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(HazelcastToolkitAutoConfiguration.class)
@ConditionalOnClass(CacheManager.class)
public class HazelcastJCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager jCacheManager(HazelcastInstance hazelcastInstance) {
        CachingProvider cachingProvider = new HazelcastCachingProvider();
        Properties properties = HazelcastCachingProvider.propertiesByInstanceItself(hazelcastInstance);
        return cachingProvider.getCacheManager(
                cachingProvider.getDefaultURI(),
                cachingProvider.getDefaultClassLoader(),
                properties
        );
    }

    @Bean
    @ConditionalOnMissingBean(org.springframework.cache.CacheManager.class)
    public org.springframework.cache.CacheManager springCacheManager(CacheManager cacheManager) {
        JCacheCacheManager springCacheManager = new JCacheCacheManager();
        springCacheManager.setCacheManager(cacheManager);
        return springCacheManager;
    }
}
