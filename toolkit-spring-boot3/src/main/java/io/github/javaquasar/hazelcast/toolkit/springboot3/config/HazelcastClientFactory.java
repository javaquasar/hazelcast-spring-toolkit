package io.github.javaquasar.hazelcast.toolkit.springboot3.config;

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
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Creates a single Hazelcast client instance.
 *
 * NOTE: for Hibernate L2 cache you might want to reuse the same client instance by instanceName.
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

    public HazelcastInstance createClient(HazelcastClientProperties props, HzToolkitProperties toolkitProps) {
        return HazelcastClient.newHazelcastClient(createClientConfig(props, toolkitProps));
    }

    ClientConfig createClientConfig(HazelcastClientProperties props, HzToolkitProperties toolkitProps) {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setClusterName(props.getClusterName());
        clientConfig.setInstanceName(props.getInstanceName());
        clientConfig.getNetworkConfig().setAddresses(props.getNetwork().getClusterMembers());
        if (!props.getNetwork().isSmartRouting()) {
            clientConfig.getNetworkConfig().getClusterRoutingConfig().setRoutingMode(RoutingMode.SINGLE_MEMBER);
        }

        registerCompactTypes(clientConfig, toolkitProps);
        customizers.forEach(customizer -> customizer.customize(clientConfig));
        return clientConfig;
    }

    private void registerCompactTypes(ClientConfig clientConfig, HzToolkitProperties toolkitProps) {
        String basePackage = toolkitProps.getCompact().getBasePackage();
        if (!StringUtils.hasText(basePackage)) {
            return;
        }

        SerializationConfig serializationConfig = clientConfig.getSerializationConfig();
        CompactSerializationConfig compact = serializationConfig.getCompactSerializationConfig();
        CompactScanResult scanResult = compactScanner.scan(basePackage);

        // Explicit serializers take precedence over zero-config reflective registration.
        scanResult.serializers().forEach(compact::addSerializer);
        scanResult.compactClasses().forEach(compact::addClass);

        log.info(
                "Registered {} @HzCompact types from basePackage={} (serializers={}, reflectiveClasses={})",
                scanResult.serializers().size() + scanResult.compactClasses().size(),
                basePackage,
                scanResult.serializers().size(),
                scanResult.compactClasses().size()
        );
    }
}
