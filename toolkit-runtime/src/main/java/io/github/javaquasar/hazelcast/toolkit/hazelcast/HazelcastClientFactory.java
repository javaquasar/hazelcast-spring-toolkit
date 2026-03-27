package io.github.javaquasar.hazelcast.toolkit.hazelcast;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.impl.connection.tcp.RoutingMode;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.compact.CompactClientConfigSupport;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.compat.CompactClassesScanner;

import java.util.List;

/**
 * Creates a single Hazelcast client instance.
 */
public class HazelcastClientFactory {

    private final CompactClientConfigSupport compactSupport;
    private final List<HazelcastClientConfigCustomizer> customizers;

    public HazelcastClientFactory(CompactClassesScanner compactScanner) {
        this(new CompactClientConfigSupport(compactScanner), List.of());
    }

    public HazelcastClientFactory(CompactClassesScanner compactScanner,
                                  List<HazelcastClientConfigCustomizer> customizers) {
        this(new CompactClientConfigSupport(compactScanner), customizers);
    }

    public HazelcastClientFactory(CompactClientConfigSupport compactSupport,
                                  List<HazelcastClientConfigCustomizer> customizers) {
        this.compactSupport = compactSupport;
        this.customizers = List.copyOf(customizers);
    }

    public HazelcastInstance createClient(String instanceName,
                                          String clusterName,
                                          List<String> clusterMembers,
                                          boolean smartRouting,
                                          String compactBasePackage) {
        return createClient(instanceName, null, clusterName, clusterMembers, smartRouting, compactBasePackage);
    }

    public HazelcastInstance createClient(String baseName,
                                          String applicationName,
                                          String clusterName,
                                          List<String> clusterMembers,
                                          boolean smartRouting,
                                          String compactBasePackage) {
        return HazelcastClient.newHazelcastClient(createClientConfig(
                baseName,
                applicationName,
                clusterName,
                clusterMembers,
                smartRouting,
                compactBasePackage
        ));
    }

    public ClientConfig createClientConfig(String instanceName,
                                           String clusterName,
                                           List<String> clusterMembers,
                                           boolean smartRouting,
                                           String compactBasePackage) {
        return createClientConfig(instanceName, null, clusterName, clusterMembers, smartRouting, compactBasePackage);
    }

    public ClientConfig createClientConfig(String baseName,
                                           String applicationName,
                                           String clusterName,
                                           List<String> clusterMembers,
                                           boolean smartRouting,
                                           String compactBasePackage) {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setClusterName(clusterName);
        clientConfig.setInstanceName(HazelcastClientNameBuilder.build(baseName, applicationName));
        clientConfig.getNetworkConfig().setAddresses(clusterMembers);
        if (!smartRouting) {
            clientConfig.getNetworkConfig().getClusterRoutingConfig().setRoutingMode(RoutingMode.SINGLE_MEMBER);
        }

        compactSupport.registerCompactTypes(clientConfig.getSerializationConfig(), compactBasePackage);
        customizers.forEach(customizer -> customizer.customize(clientConfig));
        return clientConfig;
    }
}
