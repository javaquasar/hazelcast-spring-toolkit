package io.github.javaquasar.hazelcast.toolkit.hazelcast;

import java.util.Locale;

/**
 * Builds a deterministic, DNS-safe Hazelcast client instance name from an optional
 * base name and an optional Spring application name.
 *
 * <h2>Naming rules</h2>
 * <ol>
 *   <li>Each component is trimmed, lower-cased, and reduced to {@code [a-z0-9-]}
 *       (consecutive non-alphanumeric characters become a single {@code -}; leading
 *       and trailing dashes are stripped).
 *   <li>If {@code baseName} is blank or {@code null} after sanitization, the default
 *       {@value #DEFAULT_BASE_NAME} is used.
 *   <li>If {@code applicationName} is blank or {@code null} after sanitization, the
 *       final name equals the sanitized base name alone.
 *   <li>Otherwise the final name is {@code <baseName>-<applicationName>}.
 * </ol>
 *
 * <h2>Examples</h2>
 * <pre>
 * build("MyClient", "Order Service")  → "myclient-order-service"
 * build(null,       "Order Service")  → "app-hz-client-order-service"
 * build("MyClient", null)             → "myclient"
 * build(null,       null)             → "app-hz-client"
 * </pre>
 *
 * <p>The resulting name is used as the Hazelcast instance name, which must be unique
 * within a JVM.  In test environments with multiple Spring contexts, ensure each
 * context receives a distinct {@code hazelcast.client.instance-name} property to
 * avoid {@code HazelcastClientInstance with name '...' already exists} errors.
 *
 * @since 0.1.0
 */
public final class HazelcastClientNameBuilder {

    private static final String DEFAULT_BASE_NAME = "app-hz-client";

    private HazelcastClientNameBuilder() {
    }

    public static String build(String baseName, String applicationName) {
        String safeBaseName = sanitizeBaseName(baseName);
        String safeApplicationName = sanitizeApplicationName(applicationName);

        if (safeApplicationName.isBlank()) {
            return safeBaseName;
        }

        return safeBaseName + "-" + safeApplicationName;
    }

    static String sanitizeBaseName(String baseName) {
        String sanitized = sanitize(baseName);
        return sanitized.isBlank() ? DEFAULT_BASE_NAME : sanitized;
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
}
