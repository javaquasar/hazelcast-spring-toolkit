package io.github.javaquasar.hazelcast.toolkit.hazelcast;

import java.util.Locale;
import java.util.UUID;

/**
 * Builds a Hazelcast client instance name from the toolkit naming policy,
 * an optional explicit Hazelcast instance name, and an optional Spring
 * application name.
 *
 * <h2>Naming rules</h2>
 * <ol>
 *   <li>Each toolkit-managed component is trimmed, lower-cased, and reduced to
 *       {@code [a-z0-9-]} (consecutive non-alphanumeric characters become a
 *       single {@code -}; leading and trailing dashes are stripped).</li>
 *   <li>If {@code baseName} is present after sanitization, it acts as the naming
 *       policy prefix. When {@code applicationName} is also present, the final
 *       name is {@code <baseName>-<applicationName>}; otherwise the final name
 *       equals the sanitized base name alone.</li>
 *   <li>If no toolkit {@code baseName} is configured but an explicit Hazelcast
 *       {@code instanceName} is set, that explicit name is used as-is.</li>
 *   <li>If neither toolkit {@code baseName} nor explicit {@code instanceName} is
 *       set, but {@code applicationName} is present, the final name equals the
 *       sanitized application name.</li>
 *   <li>If no naming input is configured, a unique fallback is generated instead
 *       of reusing a JVM-wide shared default.</li>
 * </ol>
 */
public final class HazelcastClientNameBuilder {

    private static final String GENERATED_FALLBACK_PREFIX = "hz-client";

    private HazelcastClientNameBuilder() {
    }

    public static String build(String baseName, String applicationName) {
        return build(baseName, null, applicationName);
    }

    public static String build(String baseName, String explicitInstanceName, String applicationName) {
        String safeBaseName = sanitizeBaseName(baseName);
        if (!safeBaseName.isBlank()) {
            String safeApplicationName = sanitizeApplicationName(applicationName);
            return safeApplicationName.isBlank()
                    ? safeBaseName
                    : safeBaseName + "-" + safeApplicationName;
        }

        if (explicitInstanceName != null && !explicitInstanceName.isBlank()) {
            return explicitInstanceName;
        }

        String safeApplicationName = sanitizeApplicationName(applicationName);
        if (!safeApplicationName.isBlank()) {
            return safeApplicationName;
        }

        return generateFallbackName();
    }

    static String sanitizeBaseName(String baseName) {
        return sanitize(baseName);
    }

    static String sanitizeApplicationName(String applicationName) {
        return sanitize(applicationName);
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }

        return value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
    }

    private static String generateFallbackName() {
        return GENERATED_FALLBACK_PREFIX + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
