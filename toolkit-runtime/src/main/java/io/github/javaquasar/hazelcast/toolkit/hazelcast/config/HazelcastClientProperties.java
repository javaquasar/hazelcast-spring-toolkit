package io.github.javaquasar.hazelcast.toolkit.hazelcast.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties bound under the {@code hazelcast.client} prefix.
 *
 * <p>These properties control the low-level Hazelcast client connection.
 * All fields have sensible defaults for local development but must be overridden
 * for any non-local environment.
 *
 * <p>Example {@code application.yml}:
 * <pre>{@code
 * hazelcast:
 *   client:
 *     instance-name: my-service-hz
 *     cluster-name: production
 *     network:
 *       cluster-members:
 *         - hz-node-1:5701
 *         - hz-node-2:5701
 *       smart-routing: true
 * }</pre>
 *
 * @see io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientFactory
 * @since 0.1.0
 */
public class HazelcastClientProperties {

    private String instanceName = "app-hz-client";
    private String clusterName = "dev";
    private Network network = new Network();

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public Network getNetwork() {
        return network;
    }

    public void setNetwork(Network network) {
        this.network = network;
    }

    /**
     * Network connection settings for the Hazelcast client.
     */
    public static class Network {
        private List<String> clusterMembers = new ArrayList<>();
        private boolean smartRouting = true;

        public List<String> getClusterMembers() {
            return clusterMembers;
        }

        public void setClusterMembers(List<String> clusterMembers) {
            this.clusterMembers = clusterMembers;
        }

        public boolean isSmartRouting() {
            return smartRouting;
        }

        public void setSmartRouting(boolean smartRouting) {
            this.smartRouting = smartRouting;
        }
    }
}
