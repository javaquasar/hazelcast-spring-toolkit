package io.github.javaquasar.hazelcast.toolkit.springboot3.config;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.CompactSerializationConfig;
import com.hazelcast.config.SerializationConfig;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.compat.CompactClassesScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Creates a single Hazelcast client instance.
 *
 * NOTE: for Hibernate L2 cache you might want to reuse the same client instance by instanceName.
 */
public class HazelcastClientFactory {

    private static final Logger log = LoggerFactory.getLogger(HazelcastClientFactory.class);

    private final CompactClassesScanner compactScanner;

    public HazelcastClientFactory(CompactClassesScanner compactScanner) {
        this.compactScanner = compactScanner;
    }

    public HazelcastInstance createClient(HazelcastClientProperties props, HzToolkitProperties toolkitProps) {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setClusterName(props.getClusterName());
        clientConfig.setInstanceName(props.getInstanceName());
        clientConfig.getNetworkConfig().setAddresses(props.getNetwork().getClusterMembers());

        // Compact registration
        String basePackage = toolkitProps.getCompact().getBasePackage();
        if (StringUtils.hasText(basePackage)) {
            SerializationConfig serializationConfig = clientConfig.getSerializationConfig();
            CompactSerializationConfig compact = serializationConfig.getCompactSerializationConfig();

            var types = compactScanner.scanCompactSchemas(basePackage);
            types.forEach(compact::addClass);

            log.info("Registered {} @HzCompact types from basePackage={}", types.size(), basePackage);
        }

        return HazelcastClient.newHazelcastClient(clientConfig);
    }
}
