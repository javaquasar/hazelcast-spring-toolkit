package io.github.javaquasar.hazelcast.toolkit.boot3.l2;

import com.hazelcast.cache.HazelcastCachingProvider;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.EvictionConfig;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.MaxSizePolicy;
import com.hazelcast.config.NearCacheConfig;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientConfigCustomizer;
import org.hibernate.cache.jcache.ConfigSettings;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;
import java.util.Properties;

@TestConfiguration
public class L2CacheTestConfiguration {

    @Bean
    @Primary
    public CacheManager cacheManager(HazelcastInstance hazelcastInstance) {
        CachingProvider cachingProvider = new HazelcastCachingProvider();
        Properties properties = HazelcastCachingProvider.propertiesByInstanceItself(hazelcastInstance);
        CacheManager cacheManager = cachingProvider.getCacheManager(
                cachingProvider.getDefaultURI(),
                cachingProvider.getDefaultClassLoader(),
                properties
        );

        Cache<Object, Object> cache = cacheManager.getCache(TestCachedEntity.CACHE_REGION);
        if (cache == null) {
            cacheManager.createCache(TestCachedEntity.CACHE_REGION,
                    new MutableConfiguration<>()
                            .setStoreByValue(false)
                            .setStatisticsEnabled(true));
        }

        return cacheManager;
    }

    @Bean
    public HazelcastClientConfigCustomizer l2NearCacheCustomizer() {
        return this::customizeNearCache;
    }

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer(CacheManager cacheManager) {
        return properties -> {
            properties.put("javax.persistence.sharedCache.mode", "ENABLE_SELECTIVE");
            properties.put(AvailableSettings.USE_SECOND_LEVEL_CACHE, true);
            properties.put(AvailableSettings.USE_QUERY_CACHE, false);
            properties.put(AvailableSettings.CACHE_REGION_FACTORY, "org.hibernate.cache.jcache.internal.JCacheRegionFactory");
            properties.put("hibernate.javax.cache.provider", HazelcastCachingProvider.class.getName());
            properties.put(ConfigSettings.CACHE_MANAGER, cacheManager);
            properties.put("hibernate.javax.cache.cache_manager", cacheManager);
            properties.put("hibernate.javax.cache.missing_cache_strategy", "create");
            properties.put(AvailableSettings.GENERATE_STATISTICS, true);
            properties.put("hibernate.cache.use_structured_entries", true);
        };
    }

    private void customizeNearCache(ClientConfig clientConfig) {
        NearCacheConfig nearCacheConfig = new NearCacheConfig(TestCachedEntity.CACHE_REGION);
        nearCacheConfig.setInvalidateOnChange(true);
        nearCacheConfig.setTimeToLiveSeconds(0);
        nearCacheConfig.setMaxIdleSeconds(0);
        nearCacheConfig.setEvictionConfig(new EvictionConfig()
                .setEvictionPolicy(EvictionPolicy.LRU)
                .setMaxSizePolicy(MaxSizePolicy.ENTRY_COUNT)
                .setSize(10_000));

        clientConfig.addNearCacheConfig(nearCacheConfig);
    }
}
