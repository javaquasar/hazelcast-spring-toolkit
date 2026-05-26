package io.github.javaquasar.hazelcast.toolkit.springboot4.actuator;

import com.hazelcast.cluster.Member;
import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.lang.reflect.Method;

public class HazelcastToolkitHealthIndicator implements HealthIndicator {

    private final HazelcastInstance hazelcastInstance;

    public HazelcastToolkitHealthIndicator(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    @Override
    public Health health() {
        try {
            String mode = resolveMode();
            boolean lifecycleRunning = safeLifecycleRunning();
            int memberCount = safeMemberCount();

            Health.Builder builder = lifecycleRunning && memberCount > 0 ? Health.up() : Health.down();
            builder
                    .withDetail("instanceName", hazelcastInstance.getName())
                    .withDetail("mode", mode)
                    .withDetail("clusterName", safeClusterName())
                    .withDetail("lifecycleRunning", lifecycleRunning)
                    .withDetail("clusterState", safeClusterState())
                    .withDetail("memberCount", memberCount);
            if ("client".equals(mode)) {
                builder.withDetail("connected", lifecycleRunning && memberCount > 0);
            }
            return builder.build();
        } catch (RuntimeException ex) {
            return Health.down(ex)
                    .withDetail("instanceName", safeInstanceName())
                    .build();
        }
    }

    private boolean safeLifecycleRunning() {
        try {
            return hazelcastInstance.getLifecycleService().isRunning();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private int safeMemberCount() {
        try {
            return hazelcastInstance.getCluster().getMembers().size();
        } catch (RuntimeException ex) {
            return -1;
        }
    }

    private String safeClusterState() {
        try {
            return String.valueOf(hazelcastInstance.getCluster().getClusterState());
        } catch (RuntimeException ex) {
            return "unknown";
        }
    }

    private String safeInstanceName() {
        try {
            return hazelcastInstance.getName();
        } catch (RuntimeException ex) {
            return "unknown";
        }
    }

    private String resolveMode() {
        try {
            Object localEndpoint = hazelcastInstance.getLocalEndpoint();
            return localEndpoint instanceof Member ? "member" : "client";
        } catch (RuntimeException ex) {
            return "unknown";
        }
    }

    private String safeClusterName() {
        String memberClusterName = clusterNameFromMemberConfig();
        if (memberClusterName != null) {
            return memberClusterName;
        }
        String clientClusterName = clusterNameFromClientConfig();
        return clientClusterName != null ? clientClusterName : "unknown";
    }

    private String clusterNameFromMemberConfig() {
        try {
            Config config = hazelcastInstance.getConfig();
            return config != null ? config.getClusterName() : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String clusterNameFromClientConfig() {
        try {
            Method getClientConfig = findMethod(hazelcastInstance.getClass(), "getClientConfig");
            Object clientConfig = getClientConfig.invoke(hazelcastInstance);
            Method getClusterName = findMethod(clientConfig.getClass(), "getClusterName");
            Object clusterName = getClusterName.invoke(clientConfig);
            return clusterName != null ? clusterName.toString() : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name) throws NoSuchMethodException {
        Method method = type.getMethod(name);
        method.setAccessible(true);
        return method;
    }
}
