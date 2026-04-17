package io.github.javaquasar.hazelcast.toolkit.boot3.l2;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.EvictionConfig;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.MaxSizePolicy;
import com.hazelcast.config.NearCacheConfig;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientConfigCustomizer;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntity;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Test infrastructure for Hazelcast L2 cache tests.
 *
 * <p>Provides only the near-cache client customizer — the {@code javax.cache.CacheManager} and
 * {@link org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer} are
 * intentionally omitted here and instead come from the production auto-configuration
 * ({@code HazelcastJCacheAutoConfiguration} and {@code HazelcastHibernateL2AutoConfiguration}).
 * Activate them with {@code hazelcast.toolkit.hibernate.l2.enabled=true}.
 */
@TestConfiguration
public class L2CacheTestConfiguration {

    @Bean
    public HazelcastClientConfigCustomizer l2NearCacheCustomizer(Environment environment) {
        boolean nearCacheEnabled = environment.getProperty("test.hazelcast.near-cache.enabled", Boolean.class, true);
        return nearCacheEnabled ? this::customizeNearCache : clientConfig -> {
        };
    }

    private void customizeNearCache(ClientConfig clientConfig) {
        NearCacheConfig nearCacheConfig = new NearCacheConfig(SharedTestCachedEntity.CACHE_REGION);
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
