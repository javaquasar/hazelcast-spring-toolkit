package io.github.javaquasar.hazelcast.toolkit.springboot2.actuator;

import com.hazelcast.core.HazelcastInstance;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

public class HazelcastToolkitHealthIndicator implements HealthIndicator {

    private final HazelcastInstance hazelcastInstance;

    public HazelcastToolkitHealthIndicator(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    @Override
    public Health health() {
        try {
            boolean lifecycleRunning = hazelcastInstance.getLifecycleService().isRunning();
            int memberCount = hazelcastInstance.getCluster().getMembers().size();
            Object clusterState = hazelcastInstance.getCluster().getClusterState();

            Health.Builder builder = lifecycleRunning && memberCount > 0 ? Health.up() : Health.down();
            return builder
                    .withDetail("instanceName", hazelcastInstance.getName())
                    .withDetail("lifecycleRunning", lifecycleRunning)
                    .withDetail("clusterState", String.valueOf(clusterState))
                    .withDetail("memberCount", memberCount)
                    .build();
        } catch (RuntimeException ex) {
            return Health.down(ex)
                    .withDetail("instanceName", safeInstanceName())
                    .build();
        }
    }

    private String safeInstanceName() {
        try {
            return hazelcastInstance.getName();
        } catch (RuntimeException ex) {
            return "unknown";
        }
    }
}
