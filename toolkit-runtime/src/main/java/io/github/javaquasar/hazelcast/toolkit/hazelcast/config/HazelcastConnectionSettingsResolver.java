package io.github.javaquasar.hazelcast.toolkit.hazelcast.config;

import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties.Instance.Mode;

import java.util.List;

/**
 * Resolves shared client/member connection settings while retaining legacy
 * mode-specific properties as fallbacks.
 *
 * @since 0.12.0
 */
public final class HazelcastConnectionSettingsResolver {

    private HazelcastConnectionSettingsResolver() {
    }

    public static String clusterName(
            HzToolkitProperties toolkitProperties,
            HazelcastClientProperties clientProperties,
            Mode mode) {
        if (hasText(toolkitProperties.getClusterName())) {
            return toolkitProperties.getClusterName();
        }
        return mode == Mode.MEMBER
                ? toolkitProperties.getMember().getClusterName()
                : clientProperties.getClusterName();
    }

    public static List<String> seedMembers(
            HzToolkitProperties toolkitProperties,
            HazelcastClientProperties clientProperties,
            Mode mode) {
        List<String> sharedSeedMembers = toolkitProperties.getNetwork().getSeedMembers();
        if (sharedSeedMembers != null && !sharedSeedMembers.isEmpty()) {
            return List.copyOf(sharedSeedMembers);
        }
        List<String> legacySeedMembers = mode == Mode.MEMBER
                ? toolkitProperties.getMember().getNetwork().getJoin().getTcpIpMembers()
                : clientProperties.getNetwork().getClusterMembers();
        return legacySeedMembers == null ? List.of() : List.copyOf(legacySeedMembers);
    }

    public static String enterpriseLicenseKey(
            HzToolkitProperties toolkitProperties,
            HazelcastClientProperties clientProperties) {
        return hasText(toolkitProperties.getEnterpriseLicenseKey())
                ? toolkitProperties.getEnterpriseLicenseKey()
                : clientProperties.getEnterpriseLicenseKey();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
