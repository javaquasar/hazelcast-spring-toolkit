package io.github.javaquasar.hazelcast.toolkit.springboot3.config;

import com.hazelcast.cache.HazelcastCachingProvider;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;

import javax.cache.CacheManager;
import javax.cache.spi.CachingProvider;
import java.util.Properties;

/**
 * Auto-configuration that wires a {@code javax.cache.CacheManager} to the toolkit-managed
 * {@link HazelcastInstance} using
 * {@link HazelcastCachingProvider#propertiesByInstanceItself(HazelcastInstance)}.
 *
 * <p>This guarantees the JCache manager is bound to the exact live client instance created by
 * {@link HazelcastToolkitAutoConfiguration}, rather than relying on instance-name lookup which
 * is fragile when multiple clients share the same JVM.
 *
 * <p>A Spring {@link org.springframework.cache.CacheManager} backed by {@link JCacheCacheManager}
 * is also registered so that Spring's {@code @Cacheable} / {@code @CacheEvict} annotations work
 * out of the box.
 *
 * @see HazelcastHibernateL2AutoConfiguration
 */
@AutoConfiguration
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
