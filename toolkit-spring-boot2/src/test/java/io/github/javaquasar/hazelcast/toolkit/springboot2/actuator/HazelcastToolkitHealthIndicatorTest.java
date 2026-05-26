package io.github.javaquasar.hazelcast.toolkit.springboot2.actuator;

import com.hazelcast.cluster.Cluster;
import com.hazelcast.cluster.ClusterState;
import com.hazelcast.cluster.Member;
import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.LifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.lang.reflect.Proxy;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class HazelcastToolkitHealthIndicatorTest {

    @Test
    void reportsUpWhenLifecycleRunsAndMembersAreVisible() {
        Health health = new HazelcastToolkitHealthIndicator(hazelcastInstance(true, 1, false)).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("instanceName", "test-hazelcast");
        assertThat(health.getDetails()).containsEntry("mode", "member");
        assertThat(health.getDetails()).containsEntry("clusterName", "test-cluster");
        assertThat(health.getDetails()).containsEntry("lifecycleRunning", true);
        assertThat(health.getDetails()).containsEntry("clusterState", "ACTIVE");
        assertThat(health.getDetails()).containsEntry("memberCount", 1);
    }

    @Test
    void reportsClientModeAndConnectedStateWhenLocalEndpointIsNotMember() {
        Health health = new HazelcastToolkitHealthIndicator(hazelcastInstance(true, 1, false, false)).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("mode", "client");
        assertThat(health.getDetails()).containsEntry("clusterName", "test-cluster");
        assertThat(health.getDetails()).containsEntry("memberCount", 1);
        assertThat(health.getDetails()).containsEntry("connected", true);
    }

    @Test
    void reportsClientModeAsDisconnectedWhenClusterInspectionFails() {
        Health health = new HazelcastToolkitHealthIndicator(hazelcastInstance(true, 1, true, false)).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("mode", "client");
        assertThat(health.getDetails()).containsEntry("clusterName", "test-cluster");
        assertThat(health.getDetails()).containsEntry("lifecycleRunning", true);
        assertThat(health.getDetails()).containsEntry("clusterState", "unknown");
        assertThat(health.getDetails()).containsEntry("memberCount", -1);
        assertThat(health.getDetails()).containsEntry("connected", false);
    }

    @Test
    void reportsDownWhenLifecycleIsStopped() {
        Health health = new HazelcastToolkitHealthIndicator(hazelcastInstance(false, 1, false)).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("lifecycleRunning", false);
        assertThat(health.getDetails()).containsEntry("memberCount", 1);
    }

    @Test
    void reportsDownWhenNoMembersAreVisible() {
        Health health = new HazelcastToolkitHealthIndicator(hazelcastInstance(true, 0, false)).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("lifecycleRunning", true);
        assertThat(health.getDetails()).containsEntry("memberCount", 0);
    }

    @Test
    void reportsDownWhenClusterInspectionFails() {
        Health health = new HazelcastToolkitHealthIndicator(hazelcastInstance(true, 1, true)).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("instanceName", "test-hazelcast");
        assertThat(health.getDetails()).containsEntry("clusterState", "unknown");
        assertThat(health.getDetails()).containsEntry("memberCount", -1);
    }

    private static HazelcastInstance hazelcastInstance(boolean running, int memberCount, boolean failCluster) {
        return hazelcastInstance(running, memberCount, failCluster, true);
    }

    private static HazelcastInstance hazelcastInstance(boolean running, int memberCount, boolean failCluster, boolean memberMode) {
        LifecycleService lifecycleService = proxy(LifecycleService.class, (proxy, method, args) -> {
            if ("isRunning".equals(method.getName())) {
                return running;
            }
            return defaultValue(method.getReturnType());
        });
        Cluster cluster = proxy(Cluster.class, (proxy, method, args) -> {
            if (failCluster) {
                throw new IllegalStateException("cluster unavailable");
            }
            if ("getMembers".equals(method.getName())) {
                return memberCount > 0
                        ? Collections.singleton(member())
                        : Collections.emptySet();
            }
            if ("getClusterState".equals(method.getName())) {
                return ClusterState.ACTIVE;
            }
            return defaultValue(method.getReturnType());
        });
        return proxy(HazelcastInstance.class, (proxy, method, args) -> {
            if ("getName".equals(method.getName())) {
                return "test-hazelcast";
            }
            if ("getLifecycleService".equals(method.getName())) {
                return lifecycleService;
            }
            if ("getCluster".equals(method.getName())) {
                return cluster;
            }
            if ("getConfig".equals(method.getName())) {
                Config config = new Config();
                config.setClusterName("test-cluster");
                return config;
            }
            if ("getLocalEndpoint".equals(method.getName())) {
                return memberMode ? member() : proxy(method.getReturnType(), (endpointProxy, endpointMethod, endpointArgs) ->
                        defaultValue(endpointMethod.getReturnType()));
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static Member member() {
        return proxy(Member.class, (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> returnType) {
        return switch (returnType.getName()) {
            case "boolean" -> false;
            case "int" -> 0;
            case "long" -> 0L;
            default -> null;
        };
    }
}
