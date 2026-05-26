package io.github.javaquasar.hazelcast.toolkit.hazelcast;

import com.hazelcast.config.Config;

/**
 * Callback interface for customizing toolkit-managed Hazelcast member configuration.
 *
 * @since 0.10.0
 */
@FunctionalInterface
public interface HazelcastMemberConfigCustomizer {

    /**
     * Customize the member {@link Config} before the Hazelcast member starts.
     *
     * @param config member configuration
     */
    void customize(Config config);
}
