package io.github.javaquasar.hazelcast.toolkit.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.compact.CompactClientConfigSupport;
import io.github.javaquasar.hazelcast.toolkit.scan.api.ClassScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Builds and returns a configured Hazelcast member {@link HazelcastInstance}.
 *
 * @since 0.10.0
 */
public class HazelcastMemberFactory {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastMemberFactory.class);

    private final CompactClientConfigSupport compactSupport;
    private final List<HazelcastMemberConfigCustomizer> customizers;

    public HazelcastMemberFactory(ClassScanner classScanner) {
        this(new CompactClientConfigSupport(classScanner), List.of());
    }

    public HazelcastMemberFactory(ClassScanner classScanner,
                                  List<HazelcastMemberConfigCustomizer> customizers) {
        this(new CompactClientConfigSupport(classScanner), customizers);
    }

    public HazelcastMemberFactory(CompactClientConfigSupport compactSupport,
                                  List<HazelcastMemberConfigCustomizer> customizers) {
        this.compactSupport = compactSupport;
        this.customizers = List.copyOf(customizers);
    }

    public HazelcastInstance createMember(String instanceName,
                                          String clusterName,
                                          int port,
                                          boolean portAutoIncrement,
                                          String publicAddress,
                                          boolean autoDetectionEnabled,
                                          boolean multicastEnabled,
                                          List<String> tcpIpMembers,
                                          String compactBasePackage) {
        return createMember(
                instanceName,
                clusterName,
                false,
                port,
                portAutoIncrement,
                publicAddress,
                autoDetectionEnabled,
                multicastEnabled,
                tcpIpMembers,
                compactBasePackage
        );
    }

    public HazelcastInstance createMember(String instanceName,
                                          String clusterName,
                                          boolean liteMember,
                                          int port,
                                          boolean portAutoIncrement,
                                          String publicAddress,
                                          boolean autoDetectionEnabled,
                                          boolean multicastEnabled,
                                          List<String> tcpIpMembers,
                                          String compactBasePackage) {
        Config config = createMemberConfig(
                instanceName,
                clusterName,
                liteMember,
                port,
                portAutoIncrement,
                publicAddress,
                autoDetectionEnabled,
                multicastEnabled,
                tcpIpMembers,
                compactBasePackage
        );
        return startMember(config);
    }

    public Config createMemberConfig(String instanceName,
                                     String clusterName,
                                     int port,
                                     boolean portAutoIncrement,
                                     String publicAddress,
                                     boolean autoDetectionEnabled,
                                     boolean multicastEnabled,
                                     List<String> tcpIpMembers,
                                     String compactBasePackage) {
        return createMemberConfig(
                instanceName,
                clusterName,
                false,
                port,
                portAutoIncrement,
                publicAddress,
                autoDetectionEnabled,
                multicastEnabled,
                tcpIpMembers,
                compactBasePackage
        );
    }

    public Config createMemberConfig(String instanceName,
                                     String clusterName,
                                     boolean liteMember,
                                     int port,
                                     boolean portAutoIncrement,
                                     String publicAddress,
                                     boolean autoDetectionEnabled,
                                     boolean multicastEnabled,
                                     List<String> tcpIpMembers,
                                     String compactBasePackage) {
        Config config = new Config();
        if (hasText(instanceName)) {
            config.setInstanceName(instanceName);
        }
        if (hasText(clusterName)) {
            config.setClusterName(clusterName);
        }
        config.setLiteMember(liteMember);

        NetworkConfig networkConfig = config.getNetworkConfig();
        networkConfig.setPort(port);
        networkConfig.setPortAutoIncrement(portAutoIncrement);
        if (hasText(publicAddress)) {
            networkConfig.setPublicAddress(publicAddress);
        }

        JoinConfig joinConfig = networkConfig.getJoin();
        joinConfig.getAutoDetectionConfig().setEnabled(autoDetectionEnabled);
        joinConfig.getMulticastConfig().setEnabled(multicastEnabled);
        if (tcpIpMembers != null && !tcpIpMembers.isEmpty()) {
            joinConfig.getTcpIpConfig().setEnabled(true);
            joinConfig.getTcpIpConfig().setMembers(tcpIpMembers);
        } else {
            joinConfig.getTcpIpConfig().setEnabled(false);
        }

        compactSupport.registerCompactTypes(config.getSerializationConfig(), compactBasePackage);
        customizers.forEach(customizer -> customizer.customize(config));
        return config;
    }

    private HazelcastInstance startMember(Config config) {
        HazelcastInstance instance = hasText(config.getInstanceName())
                ? Hazelcast.getOrCreateHazelcastInstance(config)
                : Hazelcast.newHazelcastInstance(config);
        logger.info(
                "Hazelcast member started - configured instance name: '{}', actual instance name: '{}', cluster: '{}'",
                config.getInstanceName(),
                instance.getName(),
                config.getClusterName()
        );
        return instance;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
