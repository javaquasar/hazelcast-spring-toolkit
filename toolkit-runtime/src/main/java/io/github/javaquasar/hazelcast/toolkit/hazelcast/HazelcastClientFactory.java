package io.github.javaquasar.hazelcast.toolkit.hazelcast;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.impl.connection.tcp.RoutingMode;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.compact.CompactClientConfigSupport;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.compat.CompactClassesScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Builds and returns a configured {@link HazelcastInstance} Hazelcast client.
 *
 * <p>Construction order for each client:
 * <ol>
 *   <li>A fresh {@link ClientConfig} is created and populated with cluster name,
 *       instance name (via {@link HazelcastClientNameBuilder}), network addresses,
 *       and routing mode.
 *   <li>Compact serialization types discovered by {@link CompactClientConfigSupport}
 *       are registered on the serialization config.
 *   <li>Each {@link HazelcastClientConfigCustomizer} is applied in order, allowing
 *       further overrides (TLS, connection retry, etc.).
 *   <li>{@link HazelcastClient#newHazelcastClient(ClientConfig)} creates and returns
 *       the live instance.
 * </ol>
 *
 * <p>The instance name is derived from {@code baseName} and {@code applicationName}
 * following the rules in {@link HazelcastClientNameBuilder}.  Within a single JVM,
 * Hazelcast forbids two clients with the same instance name — ensure each
 * Spring application context uses a unique name (important in test suites that
 * spin up multiple contexts).
 *
 * @see HazelcastClientConfigCustomizer
 * @see HazelcastClientNameBuilder
 * @since 0.1.0
 */
public class HazelcastClientFactory {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastClientFactory.class);

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
        ClientConfig config = createClientConfig(baseName, applicationName, clusterName, clusterMembers, smartRouting, compactBasePackage);
        HazelcastInstance instance = HazelcastClient.newHazelcastClient(config);
        logger.info(
                "Hazelcast client started — configured instance name: '{}', actual instance name: '{}', cluster: '{}', labels: {}",
                config.getInstanceName(),
                instance.getName(),
                config.getClusterName(),
                config.getLabels()
        );
        return instance;
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
