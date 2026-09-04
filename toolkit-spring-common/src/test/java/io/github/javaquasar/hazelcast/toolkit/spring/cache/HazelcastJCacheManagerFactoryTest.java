package io.github.javaquasar.hazelcast.toolkit.spring.cache;

import com.hazelcast.cache.HazelcastCacheManager;
import com.hazelcast.cache.impl.HazelcastServerCachingProvider;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.cache.impl.HazelcastClientCachingProvider;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties.Instance.Mode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import java.net.InetSocketAddress;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class HazelcastJCacheManagerFactoryTest {

    private CacheManager cacheManager;

    @AfterEach
    void tearDown() {
        if (cacheManager != null && !cacheManager.isClosed()) {
            cacheManager.close();
        }
        HazelcastClient.shutdownAll();
        Hazelcast.shutdownAll();
    }

    @Test
    void createsServerCacheManagerForMemberEvenWhenConfiguredModeSaysClient() {
        HazelcastInstance member = startMember(uniqueClusterName());

        cacheManager = HazelcastJCacheManagerFactory.create(member, Mode.CLIENT);

        assertInstanceOf(HazelcastServerCachingProvider.class, cacheManager.getCachingProvider());
        assertSame(member, ((HazelcastCacheManager) cacheManager).getHazelcastInstance());
        assertCacheRoundTrip(cacheManager, "member-cache");
    }

    @Test
    void createsClientCacheManagerForClientEvenWhenConfiguredModeIsNone() {
        String clusterName = uniqueClusterName();
        HazelcastInstance member = startMember(clusterName);
        HazelcastInstance client = startClient(clusterName, addressOf(member));

        cacheManager = HazelcastJCacheManagerFactory.create(client, Mode.NONE);

        assertInstanceOf(HazelcastClientCachingProvider.class, cacheManager.getCachingProvider());
        assertSame(client, ((HazelcastCacheManager) cacheManager).getHazelcastInstance());
        assertCacheRoundTrip(cacheManager, "client-cache");
    }

    private static void assertCacheRoundTrip(CacheManager manager, String cacheName) {
        javax.cache.Cache<String, String> cache = manager.createCache(
                cacheName,
                new MutableConfiguration<String, String>().setTypes(String.class, String.class)
        );
        cache.put("status", "ok");
        assertEquals("ok", cache.get("status"));
    }

    private static HazelcastInstance startMember(String clusterName) {
        Config config = new Config();
        config.setClusterName(clusterName);
        config.setInstanceName("member-" + clusterName);
        config.setProperty("hazelcast.logging.type", "slf4j");
        NetworkConfig network = config.getNetworkConfig();
        network.setPort(0);
        network.setPortAutoIncrement(false);
        JoinConfig join = network.getJoin();
        join.getAutoDetectionConfig().setEnabled(false);
        join.getMulticastConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(false);
        return Hazelcast.newHazelcastInstance(config);
    }

    private static HazelcastInstance startClient(String clusterName, String address) {
        ClientConfig config = new ClientConfig();
        config.setClusterName(clusterName);
        config.setInstanceName("client-" + clusterName);
        config.getNetworkConfig().setAddresses(java.util.List.of(address));
        return HazelcastClient.newHazelcastClient(config);
    }

    private static String addressOf(HazelcastInstance member) {
        InetSocketAddress address = (InetSocketAddress) member.getLocalEndpoint().getSocketAddress();
        return "127.0.0.1:" + address.getPort();
    }

    private static String uniqueClusterName() {
        return "jcache-mode-" + UUID.randomUUID();
    }
}
