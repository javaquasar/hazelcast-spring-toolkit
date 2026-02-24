package io.github.javaquasar.hazelcast.toolkit.springboot3.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "hazelcast.client")
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

    public static class Network {
        private List<String> clusterMembers = new ArrayList<>();

        public List<String> getClusterMembers() {
            return clusterMembers;
        }

        public void setClusterMembers(List<String> clusterMembers) {
            this.clusterMembers = clusterMembers;
        }
    }

}
