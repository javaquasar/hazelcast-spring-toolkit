package io.github.javaquasar.hazelcast.toolkit.boot4.l2;

import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.AbstractEmbeddedMemberL2CacheTestConfiguration;
import org.springframework.boot.test.context.TestConfiguration;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.ServerSocket;

@TestConfiguration
public class Boot4L2CacheTestConfiguration extends AbstractEmbeddedMemberL2CacheTestConfiguration {

    public static final String CLUSTER_NAME = "boot4-l2-test-cluster";
    private static final int MEMBER_PORT = findFreePort();
    public static final String MEMBER_ADDRESS = "127.0.0.1:" + MEMBER_PORT;

    private static volatile HazelcastInstance hazelcastMember = startMember();

    @Override
    protected String clusterName() {
        return CLUSTER_NAME;
    }

    @Override
    protected String memberAddress() {
        return MEMBER_ADDRESS;
    }

    @Override
    protected void ensureTestMemberRunning() {
        doEnsureMemberRunning();
    }

    @PreDestroy
    void shutdownMember() {
        HazelcastInstance member = hazelcastMember;
        if (member != null && member.getLifecycleService().isRunning()) {
            member.shutdown();
        }
    }

    private static synchronized void doEnsureMemberRunning() {
        if (hazelcastMember == null || !hazelcastMember.getLifecycleService().isRunning()) {
            hazelcastMember = startMember();
        }
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

    private static int findFreePort() {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to allocate a free port for Hazelcast test member", exception);
        }
    }
}
