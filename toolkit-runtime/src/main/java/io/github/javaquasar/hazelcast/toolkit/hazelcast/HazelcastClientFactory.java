package io.github.javaquasar.hazelcast.toolkit.hazelcast;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.RoutingMode;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.compact.CompactClientConfigSupport;
import io.github.javaquasar.hazelcast.toolkit.scan.api.ClassScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Builds and returns a configured {@link HazelcastInstance} Hazelcast client.
 */
public class HazelcastClientFactory {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastClientFactory.class);

    private final CompactClientConfigSupport compactSupport;
    private final List<HazelcastClientConfigCustomizer> customizers;

    public HazelcastClientFactory(ClassScanner classScanner) {
        this(new CompactClientConfigSupport(classScanner), List.of());
    }

    public HazelcastClientFactory(ClassScanner classScanner,
                                  List<HazelcastClientConfigCustomizer> customizers) {
        this(new CompactClientConfigSupport(classScanner), customizers);
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
        ClientConfig config = createClientConfig(instanceName, clusterName, clusterMembers, smartRouting, compactBasePackage);
        return startClient(config);
    }

    public HazelcastInstance createClient(String baseName,
                                          String explicitInstanceName,
                                          String applicationName,
                                          String clusterName,
                                          List<String> clusterMembers,
                                          boolean smartRouting,
                                          String compactBasePackage) {
        ClientConfig config = createClientConfig(
                baseName,
                explicitInstanceName,
                applicationName,
                clusterName,
                clusterMembers,
                smartRouting,
                compactBasePackage
        );
        return startClient(config);
    }

    public HazelcastInstance createClient(String baseName,
                                          String applicationName,
                                          String clusterName,
                                          List<String> clusterMembers,
                                          boolean smartRouting,
                                          String compactBasePackage) {
        return createClient(baseName, null, applicationName, clusterName, clusterMembers, smartRouting, compactBasePackage);
    }

    public ClientConfig createClientConfig(String instanceName,
                                           String clusterName,
                                           List<String> clusterMembers,
                                           boolean smartRouting,
                                           String compactBasePackage) {
        ClientConfig clientConfig = baseClientConfig(clusterName, clusterMembers, smartRouting, compactBasePackage);
        clientConfig.setInstanceName(instanceName);
        return clientConfig;
    }

    public ClientConfig createClientConfig(String baseName,
                                           String explicitInstanceName,
                                           String applicationName,
                                           String clusterName,
                                           List<String> clusterMembers,
                                           boolean smartRouting,
                                           String compactBasePackage) {
        ClientConfig clientConfig = baseClientConfig(clusterName, clusterMembers, smartRouting, compactBasePackage);
        clientConfig.setInstanceName(HazelcastClientNameBuilder.build(baseName, explicitInstanceName, applicationName));
        return clientConfig;
    }

    public ClientConfig createClientConfig(String baseName,
                                           String applicationName,
                                           String clusterName,
                                           List<String> clusterMembers,
                                           boolean smartRouting,
                                           String compactBasePackage) {
        return createClientConfig(baseName, null, applicationName, clusterName, clusterMembers, smartRouting, compactBasePackage);
    }

    private ClientConfig baseClientConfig(String clusterName,
                                          List<String> clusterMembers,
                                          boolean smartRouting,
                                          String compactBasePackage) {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setClusterName(clusterName);
        clientConfig.getNetworkConfig().setAddresses(clusterMembers);
        if (!smartRouting) {
            clientConfig.getNetworkConfig().getClusterRoutingConfig().setRoutingMode(RoutingMode.SINGLE_MEMBER);
        }

        compactSupport.registerCompactTypes(clientConfig.getSerializationConfig(), compactBasePackage);
        customizers.forEach(customizer -> customizer.customize(clientConfig));
        return clientConfig;
    }

    private HazelcastInstance startClient(ClientConfig config) {
        HazelcastInstance instance = HazelcastClient.newHazelcastClient(config);
        logger.info(
                "Hazelcast client started - configured instance name: '{}', actual instance name: '{}', cluster: '{}', labels: {}",
                config.getInstanceName(),
                instance.getName(),
                config.getClusterName(),
                config.getLabels()
        );
        return instance;
    }
}
