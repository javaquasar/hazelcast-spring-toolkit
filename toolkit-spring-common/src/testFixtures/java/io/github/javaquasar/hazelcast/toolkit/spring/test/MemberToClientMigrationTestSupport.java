package io.github.javaquasar.hazelcast.toolkit.spring.test;

import com.hazelcast.client.Client;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.cluster.Member;
import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared runtime verification for restarting one application from Hazelcast
 * member mode as a client while an independent cluster member remains alive.
 */
public abstract class MemberToClientMigrationTestSupport {

    private static final String MAP_NAME = "member-client-migration";
    private static final Duration MEMBER_CHANGE_TIMEOUT = Duration.ofSeconds(15);

    protected final void assertMemberToClientMigration(ApplicationModeRunner applicationRunner) {
        String clusterName = "member-client-migration-" + UUID.randomUUID();
        int anchorPort = freePort();
        HazelcastInstance anchorMember = startAnchorMember(clusterName, anchorPort);
        int applicationMemberPort = freePort();
        String anchorAddress = addressOf(anchorMember);

        try {
            applicationRunner.run(memberProperties(
                    clusterName,
                    anchorAddress,
                    applicationMemberPort
            ), application -> {
                awaitMemberCount(anchorMember, 2);
                assertInstanceOf(Member.class, application.getLocalEndpoint());
                assertTrue(application.getLifecycleService().isRunning());
                application.getMap(MAP_NAME).put("written-by", "member-application");
            });

            awaitMemberCount(anchorMember, 1);
            assertTrue(anchorMember.getLifecycleService().isRunning());
            assertEquals("member-application", anchorMember.getMap(MAP_NAME).get("written-by"));

            applicationRunner.run(clientProperties(clusterName, anchorAddress), application -> {
                assertInstanceOf(Client.class, application.getLocalEndpoint());
                assertTrue(application.getLifecycleService().isRunning());
                assertEquals(1, application.getCluster().getMembers().size());
                assertEquals("member-application", application.getMap(MAP_NAME).get("written-by"));
                application.getMap(MAP_NAME).put("written-by", "client-application");
            });

            assertTrue(anchorMember.getLifecycleService().isRunning());
            assertEquals("client-application", anchorMember.getMap(MAP_NAME).get("written-by"));
        } finally {
            HazelcastClient.shutdownAll();
            Hazelcast.shutdownAll();
        }
    }

    private static HazelcastInstance startAnchorMember(String clusterName, int port) {
        Config config = new Config();
        config.setClusterName(clusterName);
        config.setInstanceName("anchor-" + clusterName);
        config.setProperty("hazelcast.logging.type", "slf4j");
        config.setProperty("hazelcast.shutdownhook.enabled", "false");

        NetworkConfig network = config.getNetworkConfig();
        network.setPort(port);
        network.setPortAutoIncrement(false);
        network.setPublicAddress("127.0.0.1");

        JoinConfig join = network.getJoin();
        join.getAutoDetectionConfig().setEnabled(false);
        join.getMulticastConfig().setEnabled(false);
        join.getTcpIpConfig()
                .setEnabled(true)
                .setMembers(List.of("127.0.0.1:" + port));

        return Hazelcast.newHazelcastInstance(config);
    }

    private static String[] memberProperties(
            String clusterName,
            String anchorAddress,
            int applicationMemberPort
    ) {
        return new String[]{
                "spring.application.name=migration-smoke-app",
                "hazelcast.toolkit.instance.mode=member",
                "hazelcast.toolkit.member.instance-name=migration-smoke-member",
                "hazelcast.toolkit.member.cluster-name=" + clusterName,
                "hazelcast.toolkit.member.network.port=" + applicationMemberPort,
                "hazelcast.toolkit.member.network.port-auto-increment=false",
                "hazelcast.toolkit.member.network.public-address=127.0.0.1",
                "hazelcast.toolkit.member.network.join.auto-detection-enabled=false",
                "hazelcast.toolkit.member.network.join.multicast-enabled=false",
                "hazelcast.toolkit.member.network.join.tcp-ip-members[0]=" + anchorAddress
        };
    }

    private static String[] clientProperties(String clusterName, String anchorAddress) {
        return new String[]{
                "spring.application.name=migration-smoke-app",
                "hazelcast.toolkit.instance.mode=client",
                "hazelcast.toolkit.client.base-name=migration-smoke-client",
                "hazelcast.client.cluster-name=" + clusterName,
                "hazelcast.client.network.cluster-members[0]=" + anchorAddress,
                "hazelcast.client.network.smart-routing=false"
        };
    }

    private static String addressOf(HazelcastInstance member) {
        InetSocketAddress address = (InetSocketAddress) member.getLocalEndpoint().getSocketAddress();
        return "127.0.0.1:" + address.getPort();
    }

    private static void awaitMemberCount(HazelcastInstance member, int expectedCount) {
        long deadline = System.nanoTime() + MEMBER_CHANGE_TIMEOUT.toNanos();
        while (member.getCluster().getMembers().size() != expectedCount
                && System.nanoTime() < deadline) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for Hazelcast membership change", exception);
            }
        }
        assertEquals(expectedCount, member.getCluster().getMembers().size());
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to allocate a free TCP port", exception);
        }
    }

    @FunctionalInterface
    protected interface ApplicationModeRunner {

        void run(String[] properties, Consumer<HazelcastInstance> verification);
    }
}
