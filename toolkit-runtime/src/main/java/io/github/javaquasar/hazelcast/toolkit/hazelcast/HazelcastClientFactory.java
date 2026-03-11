package io.github.javaquasar.hazelcast.toolkit.hazelcast;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.impl.connection.tcp.RoutingMode;
import com.hazelcast.config.CompactSerializationConfig;
import com.hazelcast.config.SerializationConfig;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.compat.CompactClassesScanner;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.compat.CompactScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Creates a single Hazelcast client instance.
 */
public class HazelcastClientFactory {

    private static final Logger log = LoggerFactory.getLogger(HazelcastClientFactory.class);

    private final CompactClassesScanner compactScanner;
    private final List<HazelcastClientConfigCustomizer> customizers;

    public HazelcastClientFactory(CompactClassesScanner compactScanner) {
        this(compactScanner, List.of());
    }

    public HazelcastClientFactory(CompactClassesScanner compactScanner,
                                  List<HazelcastClientConfigCustomizer> customizers) {
        this.compactScanner = compactScanner;
        this.customizers = List.copyOf(customizers);
    }

    public HazelcastInstance createClient(String instanceName,
                                          String clusterName,
                                          List<String> clusterMembers,
                                          boolean smartRouting,
                                          String compactBasePackage) {
        return HazelcastClient.newHazelcastClient(createClientConfig(
                instanceName,
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
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setClusterName(clusterName);
        clientConfig.setInstanceName(instanceName);
        clientConfig.getNetworkConfig().setAddresses(clusterMembers);
        if (!smartRouting) {
            clientConfig.getNetworkConfig().getClusterRoutingConfig().setRoutingMode(RoutingMode.SINGLE_MEMBER);
        }

        registerCompactTypes(clientConfig, compactBasePackage);
        customizers.forEach(customizer -> customizer.customize(clientConfig));
        return clientConfig;
    }

    private void registerCompactTypes(ClientConfig clientConfig, String compactBasePackage) {
        if (compactBasePackage == null || compactBasePackage.isBlank()) {
            return;
        }

        SerializationConfig serializationConfig = clientConfig.getSerializationConfig();
        CompactSerializationConfig compact = serializationConfig.getCompactSerializationConfig();
        CompactScanResult scanResult = compactScanner.scan(compactBasePackage);

        scanResult.serializers().forEach(compact::addSerializer);
        scanResult.compactClasses().forEach(compact::addClass);

        log.info(
                "Registered {} @HzCompact types from basePackage={} (serializers={}, reflectiveClasses={})",
                scanResult.serializers().size() + scanResult.compactClasses().size(),
                compactBasePackage,
                scanResult.serializers().size(),
                scanResult.compactClasses().size()
        );
    }
}
