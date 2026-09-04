package io.github.javaquasar.hazelcast.toolkit.springboot3.config;

import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties;
import io.github.javaquasar.hazelcast.toolkit.spring.cache.HazelcastJCacheManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;

import javax.cache.CacheManager;

/**
 * Auto-configuration that wires a {@code javax.cache.CacheManager} to the toolkit-managed
 * {@link HazelcastInstance} using
 * the client or server provider selected from the live Hazelcast topology.
 *
 * <p>This guarantees the JCache manager is bound to the exact live Hazelcast instance created by
 * {@link HazelcastToolkitAutoConfiguration}, rather than relying on generic provider selection
 * or instance-name lookup.
 *
 * <p>A Spring {@link org.springframework.cache.CacheManager} backed by {@link JCacheCacheManager}
 * is also registered so that Spring's {@code @Cacheable} / {@code @CacheEvict} annotations work
 * out of the box.
 *
 * @see HazelcastHibernateL2AutoConfiguration
 */
@AutoConfiguration
@AutoConfigureAfter(HazelcastToolkitAutoConfiguration.class)
@AutoConfigureBefore(CacheAutoConfiguration.class)
@ConditionalOnClass(CacheManager.class)
public class HazelcastJCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager jCacheManager(
            HazelcastInstance hazelcastInstance,
            HzToolkitProperties toolkitProperties) {
        return HazelcastJCacheManagerFactory.create(
                hazelcastInstance,
                toolkitProperties.getInstance().getMode()
        );
    }

    @Bean
    @ConditionalOnMissingBean(org.springframework.cache.CacheManager.class)
    @ConditionalOnProperty(
            prefix = "hazelcast.toolkit.spring-cache",
            name = "mode",
            havingValue = "jcache",
            matchIfMissing = true
    )
    public org.springframework.cache.CacheManager springCacheManager(CacheManager cacheManager) {
        JCacheCacheManager springCacheManager = new JCacheCacheManager();
        springCacheManager.setCacheManager(cacheManager);
        return springCacheManager;
    }
}
