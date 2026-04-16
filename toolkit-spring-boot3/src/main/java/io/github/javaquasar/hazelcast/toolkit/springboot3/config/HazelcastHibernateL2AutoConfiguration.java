package io.github.javaquasar.hazelcast.toolkit.springboot3.config;

import com.hazelcast.cache.HazelcastCachingProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;

import javax.cache.CacheManager;

/**
 * Auto-configuration that wires Hibernate's second-level cache to the toolkit-managed
 * {@code javax.cache.CacheManager}.
 *
 * <p>Activated when {@code hazelcast.toolkit.hibernate.l2.enabled=true} is set.  All properties
 * are applied with {@code putIfAbsent} so that application-level overrides always take precedence.
 *
 * <p>Uses Hibernate 6.x's {@code org.hibernate.cache.jcache.internal.JCacheRegionFactory}.
 *
 * @see HazelcastJCacheAutoConfiguration
 */
@AutoConfiguration
@AutoConfigureAfter({HazelcastToolkitAutoConfiguration.class, HazelcastJCacheAutoConfiguration.class})
@ConditionalOnClass(HibernatePropertiesCustomizer.class)
@ConditionalOnBean(CacheManager.class)
@ConditionalOnProperty(prefix = "hazelcast.toolkit.hibernate.l2", name = "enabled", havingValue = "true")
public class HazelcastHibernateL2AutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "hazelcastHibernateL2PropertiesCustomizer")
    public HibernatePropertiesCustomizer hazelcastHibernateL2PropertiesCustomizer(CacheManager cacheManager) {
        return properties -> {
            properties.putIfAbsent("javax.persistence.sharedCache.mode", "ENABLE_SELECTIVE");
            properties.putIfAbsent("hibernate.cache.use_second_level_cache", true);
            properties.putIfAbsent("hibernate.cache.use_query_cache", true);
            properties.putIfAbsent("hibernate.cache.keys_factory", "simple");
            properties.putIfAbsent("hibernate.cache.region.factory_class", "org.hibernate.cache.jcache.internal.JCacheRegionFactory");
            properties.putIfAbsent("hibernate.javax.cache.provider", HazelcastCachingProvider.class.getName());
            properties.putIfAbsent("hibernate.javax.cache.cache_manager", cacheManager);
            properties.putIfAbsent("hibernate.javax.cache.missing_cache_strategy", "create");
            properties.putIfAbsent("hibernate.generate_statistics", true);
        };
    }
}
