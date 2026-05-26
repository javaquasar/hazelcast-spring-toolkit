package io.github.javaquasar.hazelcast.toolkit.boot3;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.springboot3.config.HazelcastToolkitAutoConfiguration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class Boot3InstanceModeRuntimeIntegrationTest {

    @AfterEach
    void tearDown() {
        HazelcastClient.shutdownAll();
        Hazelcast.shutdownAll();
    }

    @Test
    void singleSpringBootApplicationCanRunAsClient() {
        String clusterName = uniqueClusterName();
        HazelcastInstance externalMember = startExternalMember(clusterName);
        String externalAddress = addressOf(externalMember);

        contextRunner()
                .withPropertyValues(
                        "hazelcast.toolkit.instance.mode=client",
                        "hazelcast.client.cluster-name=" + clusterName,
                        "hazelcast.client.network.smart-routing=false",
                        "hazelcast.client.network.cluster-members[0]=" + externalAddress
                )
                .run(context -> {
                    HazelcastInstance appInstance = context.getBean(HazelcastInstance.class);

                    appInstance.getMap("instance-mode-client").put("status", "ok");

                    assertThat(externalMember.getCluster().getMembers()).hasSize(1);
                    assertThat(externalMember.getMap("instance-mode-client").get("status")).isEqualTo("ok");
                    assertThat(Hazelcast.getAllHazelcastInstances()).containsExactly(externalMember);
                });
    }

    @Test
    void singleSpringBootApplicationCanRunAsMember() {
        String clusterName = uniqueClusterName();
        HazelcastInstance externalMember = startExternalMember(clusterName);
        String externalAddress = addressOf(externalMember);

        contextRunner()
                .withPropertyValues(memberModeProperties(clusterName, externalAddress, "member-app"))
                .run(context -> {
                    HazelcastInstance appMember = context.getBean(HazelcastInstance.class);

                    awaitMemberCount(externalMember, 2);
                    appMember.getMap("instance-mode-member").put("status", "ok");

                    assertThat(externalMember.getMap("instance-mode-member").get("status")).isEqualTo("ok");
                    assertThat(Hazelcast.getAllHazelcastInstances()).contains(externalMember, appMember);
                });
    }

    @Test
    void mixedSpringBootApplicationsCanRunAsClientAndMember() {
        String clusterName = uniqueClusterName();
        HazelcastInstance externalMember = startExternalMember(clusterName);
        String externalAddress = addressOf(externalMember);

        contextRunner()
                .withPropertyValues(memberModeProperties(clusterName, externalAddress, "mixed-member-app"))
                .run(memberContext -> contextRunner()
                        .withPropertyValues(
                                "hazelcast.toolkit.instance.mode=client",
                                "hazelcast.client.cluster-name=" + clusterName,
                                "hazelcast.client.network.smart-routing=false",
                                "hazelcast.client.network.cluster-members[0]=" + externalAddress
                        )
                        .run(clientContext -> assertMixedTopology(externalMember, memberContext, clientContext)));
    }

    private static void assertMixedTopology(HazelcastInstance externalMember,
                                            AssertableApplicationContext memberContext,
                                            AssertableApplicationContext clientContext) {
        HazelcastInstance appMember = memberContext.getBean(HazelcastInstance.class);
        HazelcastInstance appClient = clientContext.getBean(HazelcastInstance.class);

        awaitMemberCount(externalMember, 2);
        appClient.getMap("instance-mode-mixed").put("client-key", "client-value");
        appMember.getMap("instance-mode-mixed").put("member-key", "member-value");

        assertThat(appMember.getMap("instance-mode-mixed").get("client-key")).isEqualTo("client-value");
        assertThat(appClient.getMap("instance-mode-mixed").get("member-key")).isEqualTo("member-value");
        assertThat(externalMember.getMap("instance-mode-mixed").get("client-key")).isEqualTo("client-value");
        assertThat(externalMember.getMap("instance-mode-mixed").get("member-key")).isEqualTo("member-value");
    }

    private static ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        HazelcastToolkitAutoConfiguration.class
                ));
    }

    private static String[] memberModeProperties(String clusterName, String externalAddress, String instanceName) {
        return new String[]{
                "hazelcast.toolkit.instance.mode=member",
                "hazelcast.toolkit.member.instance-name=" + instanceName,
                "hazelcast.toolkit.member.cluster-name=" + clusterName,
                "hazelcast.toolkit.member.network.port=0",
                "hazelcast.toolkit.member.network.port-auto-increment=false",
                "hazelcast.toolkit.member.network.join.auto-detection-enabled=false",
                "hazelcast.toolkit.member.network.join.multicast-enabled=false",
                "hazelcast.toolkit.member.network.join.tcp-ip-members[0]=" + externalAddress
        };
    }

    private static HazelcastInstance startExternalMember(String clusterName) {
        int port = freePort();
        Config config = new Config();
        config.setClusterName(clusterName);
        config.setInstanceName("external-" + clusterName);
        config.setProperty("hazelcast.logging.type", "slf4j");
        NetworkConfig networkConfig = config.getNetworkConfig();
        networkConfig.setPort(port);
        networkConfig.setPortAutoIncrement(false);
        networkConfig.setPublicAddress("127.0.0.1");
        JoinConfig joinConfig = networkConfig.getJoin();
        joinConfig.getAutoDetectionConfig().setEnabled(false);
        joinConfig.getMulticastConfig().setEnabled(false);
        joinConfig.getTcpIpConfig().setEnabled(true);
        joinConfig.getTcpIpConfig().setMembers(List.of("127.0.0.1:" + port));
        return Hazelcast.newHazelcastInstance(config);
    }

    private static String addressOf(HazelcastInstance member) {
        InetSocketAddress address = (InetSocketAddress) member.getLocalEndpoint().getSocketAddress();
        return "127.0.0.1:" + address.getPort();
    }

    private static void awaitMemberCount(HazelcastInstance member, int expectedMemberCount) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(member.getCluster().getMembers()).hasSize(expectedMemberCount));
    }

    private static String uniqueClusterName() {
        return "instance-mode-" + UUID.randomUUID();
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to allocate a free TCP port", ex);
        }
    }
}
