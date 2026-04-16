package io.github.javaquasar.hazelcast.toolkit.hazelcast;

import com.hazelcast.client.config.ClientConfig;

/**
 * Callback interface for customizing the {@link ClientConfig} before a
 * {@link com.hazelcast.core.HazelcastInstance} client is created.
 *
 * <p>Register one or more implementations as Spring beans to extend or override
 * the default configuration produced by {@link HazelcastClientFactory}.
 * All registered customizers are applied in {@link org.springframework.core.Ordered}
 * order before {@code HazelcastClient.newHazelcastClient(config)} is called.
 *
 * <p><b>Example</b> — add a custom serialization config:
 * <pre>{@code
 * @Bean
 * @Order(10)
 * public HazelcastClientConfigCustomizer tlsCustomizer() {
 *     return config -> config.getNetworkConfig()
 *             .setSSLConfig(new SSLConfig().setEnabled(true));
 * }
 * }</pre>
 *
 * @see HazelcastClientFactory
 * @since 0.1.0
 */
public interface HazelcastClientConfigCustomizer {

    /**
     * Apply customizations to the given {@link ClientConfig}.
     *
     * <p>The config is fully populated (cluster name, network settings, compact
     * serialization) before this method is called. Implementations may freely add,
     * replace, or remove any settings.
     *
     * @param clientConfig the config to customize; never {@code null}
     */
    void customize(ClientConfig clientConfig);
}
