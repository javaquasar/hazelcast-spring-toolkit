package io.github.javaquasar.hazelcast.toolkit.springboot3.config;

import com.hazelcast.client.config.ClientConfig;

/**
 * Extension point for applying additional Hazelcast client configuration.
 */
public interface HazelcastClientConfigCustomizer {

    void customize(ClientConfig clientConfig);
}
