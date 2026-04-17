package io.github.javaquasar.hazelcast.toolkit.springboot2.config;

import com.hazelcast.cache.HazelcastCachingProvider;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties.Hibernate.L2;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties.Hibernate.L2.RegionFactoryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.cache.CacheManager;

/**
 * Auto-configuration for Hibernate second-level cache backed by Hazelcast.
 *
 * <p>Activated when {@code hazelcast.toolkit.hibernate.l2.enabled=true} is set
 * and {@link HibernatePropertiesCustomizer} is on the classpath (i.e. Spring Data JPA
 * is present).
 *
 * <p>This is the Spring Boot 2 / Hibernate 5 variant.  The only difference from the
 * Boot 3 version is the JCache {@code RegionFactory} class name:
 * {@code org.hibernate.cache.jcache.JCacheRegionFactory} (no {@code internal} package).
 *
 * <h2>Default behaviour ({@code extended-config=false})</h2>
 * <ul>
 *   <li>{@code JCACHE} mode: only {@code hibernate.cache.use_second_level_cache=true}.</li>
 *   <li>Native modes: additionally sets {@code region.factory_class} and
 *       {@code hazelcast.instance.name} (skipped with a warning if already configured).</li>
 * </ul>
 *
 * <h2>Full wiring ({@code extended-config=true})</h2>
 * <p>Applies the complete property set using {@code putIfAbsent}.
 *
 * @see HazelcastJCacheAutoConfiguration
 * @see HzToolkitProperties.Hibernate.L2
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({HazelcastToolkitAutoConfiguration.class, HazelcastJCacheAutoConfiguration.class})
@ConditionalOnClass(HibernatePropertiesCustomizer.class)
@ConditionalOnProperty(prefix = "hazelcast.toolkit.hibernate.l2", name = "enabled", havingValue = "true")
public class HazelcastHibernateL2AutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(HazelcastHibernateL2AutoConfiguration.class);

    // Hibernate 5 path — no 'internal' subpackage
    static final String JCACHE_REGION_FACTORY =
            "org.hibernate.cache.jcache.JCacheRegionFactory";
    static final String HAZELCAST_LOCAL_REGION_FACTORY =
            "com.hazelcast.hibernate.HazelcastLocalCacheRegionFactory";
    static final String HAZELCAST_REGION_FACTORY =
            "com.hazelcast.hibernate.HazelcastCacheRegionFactory";

    @Bean
    @ConditionalOnMissingBean(name = "hazelcastHibernateL2PropertiesCustomizer")
    public HibernatePropertiesCustomizer hazelcastHibernateL2PropertiesCustomizer(
            HazelcastInstance hazelcastInstance,
            ObjectProvider<CacheManager> cacheManagerProvider,
            HzToolkitProperties toolkitProperties) {

        L2 l2 = toolkitProperties.getHibernate().getL2();
        validateNativeModeClasspath(l2.getRegionFactory());

        return properties -> {
            properties.putIfAbsent("hibernate.cache.use_second_level_cache", true);

            if (l2.isExtendedConfig()) {
                applyFullSet(properties, l2, hazelcastInstance, cacheManagerProvider);
            } else {
                applyMinimumNativeSet(properties, l2, hazelcastInstance);
            }
        };
    }

    private void applyFullSet(
            java.util.Map<String, Object> properties,
            L2 l2,
            HazelcastInstance hazelcastInstance,
            ObjectProvider<CacheManager> cacheManagerProvider) {

        warnIfRegionFactoryAlreadySet(properties);

        if (l2.getRegionFactory() == RegionFactoryType.JCACHE) {
            CacheManager cacheManager = cacheManagerProvider.getIfAvailable();
            if (cacheManager == null) {
                throw new IllegalStateException(
                        "hazelcast.toolkit.hibernate.l2.extended-config=true with region-factory=JCACHE " +
                        "requires a javax.cache.CacheManager bean. " +
                        "Ensure HazelcastJCacheAutoConfiguration is active and javax.cache is on the classpath."
                );
            }
            properties.putIfAbsent("hibernate.cache.region.factory_class", JCACHE_REGION_FACTORY);
            properties.putIfAbsent("hibernate.javax.cache.provider", HazelcastCachingProvider.class.getName());
            properties.putIfAbsent("hibernate.javax.cache.cache_manager", cacheManager);
        } else {
            properties.putIfAbsent("hibernate.cache.region.factory_class", nativeFactoryClass(l2.getRegionFactory()));
            properties.putIfAbsent("hazelcast.instance.name", hazelcastInstance.getName());
        }

        properties.putIfAbsent("hibernate.cache.use_query_cache", l2.isUseQueryCache());
        properties.putIfAbsent("hibernate.generate_statistics", l2.isUseStatistics());
    }

    private void applyMinimumNativeSet(
            java.util.Map<String, Object> properties,
            L2 l2,
            HazelcastInstance hazelcastInstance) {

        if (l2.getRegionFactory() == RegionFactoryType.JCACHE) {
            return;
        }

        if (properties.containsKey("hibernate.cache.region.factory_class")) {
            log.warn(
                    "Hazelcast toolkit Hibernate L2: region-factory={} requested but " +
                    "hibernate.cache.region.factory_class is already set to '{}'. " +
                    "Skipping region.factory_class and hazelcast.instance.name. " +
                    "To apply alongside the existing config, set hazelcast.toolkit.hibernate.l2.extended-config=true.",
                    l2.getRegionFactory(),
                    properties.get("hibernate.cache.region.factory_class")
            );
            return;
        }

        properties.putIfAbsent("hibernate.cache.region.factory_class", nativeFactoryClass(l2.getRegionFactory()));
        properties.putIfAbsent("hazelcast.instance.name", hazelcastInstance.getName());
    }

    private void warnIfRegionFactoryAlreadySet(java.util.Map<String, Object> properties) {
        Object existing = properties.get("hibernate.cache.region.factory_class");
        if (existing != null) {
            log.warn(
                    "Hazelcast toolkit Hibernate L2: extended-config=true but " +
                    "hibernate.cache.region.factory_class is already set to '{}'. " +
                    "Proceeding with putIfAbsent — the existing value will not be changed.",
                    existing
            );
        }
    }

    private static String nativeFactoryClass(RegionFactoryType type) {
        return switch (type) {
            case HAZELCAST_LOCAL -> HAZELCAST_LOCAL_REGION_FACTORY;
            case HAZELCAST -> HAZELCAST_REGION_FACTORY;
            default -> throw new IllegalArgumentException("Not a native region factory type: " + type);
        };
    }

    private static void validateNativeModeClasspath(RegionFactoryType type) {
        if (type == RegionFactoryType.JCACHE) {
            return;
        }
        String className = type == RegionFactoryType.HAZELCAST_LOCAL
                ? HAZELCAST_LOCAL_REGION_FACTORY
                : HAZELCAST_REGION_FACTORY;
        try {
            Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Hazelcast toolkit Hibernate L2: region-factory=" + type +
                    " requires '" + className + "' on the classpath. " +
                    "Add the following dependency: implementation 'com.hazelcast:hazelcast-hibernate:<version>'"
            );
        }
    }
}
