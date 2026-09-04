package io.github.javaquasar.hazelcast.toolkit.spring.cache;

import com.hazelcast.cache.HazelcastCachingProvider;
import com.hazelcast.cache.impl.HazelcastServerCachingProvider;
import com.hazelcast.client.cache.HazelcastClientCachingProvider;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastInstanceModeResolver;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties.Instance.Mode;

import javax.cache.CacheManager;
import javax.cache.spi.CachingProvider;
import java.util.Properties;

/**
 * Creates a JCache manager bound to an existing Hazelcast client or member.
 *
 * @since 0.12.0
 */
public final class HazelcastJCacheManagerFactory {

    private HazelcastJCacheManagerFactory() {
    }

    public static CacheManager create(HazelcastInstance hazelcastInstance, Mode configuredMode) {
        CachingProvider cachingProvider = cachingProvider(hazelcastInstance, configuredMode);
        Properties properties = cachingProvider instanceof HazelcastClientCachingProvider
                ? HazelcastCachingProvider.propertiesByInstanceItself(hazelcastInstance)
                : new Properties();
        return cacheManager(cachingProvider, properties);
    }

    static CachingProvider cachingProvider(HazelcastInstance hazelcastInstance, Mode configuredMode) {
        Mode actualMode = HazelcastInstanceModeResolver.resolve(hazelcastInstance, configuredMode);
        return switch (actualMode) {
            case CLIENT -> new HazelcastClientCachingProvider();
            case MEMBER -> new HazelcastServerCachingProvider(hazelcastInstance);
            case NONE -> throw new IllegalStateException(
                    "Cannot create a Hazelcast JCache manager because the supplied HazelcastInstance " +
                    "could not be identified as a client or member"
            );
        };
    }

    private static CacheManager cacheManager(CachingProvider cachingProvider, Properties properties) {
        return cachingProvider.getCacheManager(
                cachingProvider.getDefaultURI(),
                cachingProvider.getDefaultClassLoader(),
                properties
        );
    }
}
