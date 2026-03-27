package io.github.javaquasar.hazelcast.toolkit.hazelcast;

import java.util.Locale;

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
