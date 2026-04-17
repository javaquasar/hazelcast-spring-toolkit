package io.github.javaquasar.hazelcast.toolkit.boot2.l2;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.EvictionConfig;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.MaxSizePolicy;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.NearCacheConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientConfigCustomizer;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientFactory;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.compat.CompactClassesScanner;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.UUID;

@TestConfiguration
public class Boot2L2CacheTestConfiguration {

    public static final String CLUSTER_NAME = "boot2-l2-test-cluster";
    private static final int MEMBER_PORT = findFreePort();
    public static final String MEMBER_ADDRESS = "127.0.0.1:" + MEMBER_PORT;

    private static volatile HazelcastInstance hazelcastMember = startMember();

    @Bean(destroyMethod = "shutdown")
    @Primary
    public HazelcastInstance hazelcastInstance(ObjectProvider<HazelcastClientConfigCustomizer> customizers,
                                               Environment environment) {
        ensureMemberRunning();
        String baseInstanceName = environment.getProperty("hazelcast.client.instance-name", "boot2-l2-client");
        String uniqueInstanceName = baseInstanceName + "-" + UUID.randomUUID();
        return new HazelcastClientFactory(new CompactClassesScanner(), customizers.orderedStream().toList())
                .createClient(uniqueInstanceName, CLUSTER_NAME, List.of(MEMBER_ADDRESS), false, null);
    }

    @Bean
    public HazelcastClientConfigCustomizer l2NearCacheCustomizer(Environment environment) {
        boolean nearCacheEnabled = environment.getProperty("test.hazelcast.near-cache.enabled", Boolean.class, true);
        return nearCacheEnabled ? this::customizeNearCache : clientConfig -> {
        };
    }

    @PreDestroy
    void shutdownMember() {
        HazelcastInstance member = hazelcastMember;
        if (member != null && member.getLifecycleService().isRunning()) {
            member.shutdown();
        }
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

    private static HazelcastInstance startMember() {
        Config config = new Config();
        config.setClusterName(CLUSTER_NAME);
        config.setProperty("hazelcast.logging.type", "slf4j");
        NetworkConfig networkConfig = config.getNetworkConfig();
        networkConfig.setPort(MEMBER_PORT);
        networkConfig.setPortAutoIncrement(false);
        return Hazelcast.newHazelcastInstance(config);
    }

    private static synchronized void ensureMemberRunning() {
        if (hazelcastMember == null || !hazelcastMember.getLifecycleService().isRunning()) {
            hazelcastMember = startMember();
        }
    }

    private static int findFreePort() {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to allocate a free port for Hazelcast test member", exception);
        }
    }
}
