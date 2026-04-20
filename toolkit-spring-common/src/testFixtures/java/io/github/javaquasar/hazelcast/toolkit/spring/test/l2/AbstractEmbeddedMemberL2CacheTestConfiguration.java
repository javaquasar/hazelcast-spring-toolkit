package io.github.javaquasar.hazelcast.toolkit.spring.test.l2;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.EvictionConfig;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.MaxSizePolicy;
import com.hazelcast.config.NearCacheConfig;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientConfigCustomizer;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientFactory;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.compat.CompactClassesScanner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.UUID;

/**
 * Shared base for Boot 2 / Boot 4 embedded-member L2 cache test configurations.
 *
 * <p>Provides the {@link HazelcastInstance} client bean and near-cache customizer.
 * Concrete subclasses supply the cluster name and member address via abstract methods,
 * manage the static member lifecycle (start / ensure-running), and add their own
 * lifecycle annotation ({@code @javax.annotation.PreDestroy} or
 * {@code @jakarta.annotation.PreDestroy}) for member shutdown.
 *
 * <p>Not annotated with {@code @TestConfiguration} here; concrete subclasses must add it.
 */
public abstract class AbstractEmbeddedMemberL2CacheTestConfiguration {

    protected abstract String clusterName();

    protected abstract String memberAddress();

    /**
     * Must be implemented by each concrete subclass using its own {@code static synchronized}
     * method so that the lock is on the concrete class object, preventing concurrent restarts
     * of that class's member.
     */
    protected abstract void ensureTestMemberRunning();

    @Bean(destroyMethod = "shutdown")
    @Primary
    public HazelcastInstance hazelcastInstance(ObjectProvider<HazelcastClientConfigCustomizer> customizers,
                                               Environment environment) {
        ensureTestMemberRunning();
        String baseInstanceName = environment.getProperty("hazelcast.client.instance-name", "test-l2-client");
        String uniqueInstanceName = baseInstanceName + "-" + UUID.randomUUID();
        return new HazelcastClientFactory(new CompactClassesScanner(), customizers.orderedStream().toList())
                .createClient(uniqueInstanceName, clusterName(), List.of(memberAddress()), false, null);
    }

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
