package io.github.javaquasar.hazelcast.toolkit.hazelcast.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringConfigurationMetadataTest {

    @Test
    void metadataContainsAllPublicToolkitProperties() throws IOException {
        String metadata = readMetadata();

        for (String property : expectedProperties()) {
            assertTrue(
                    metadata.contains("\"name\": \"" + property + "\""),
                    () -> "Missing Spring configuration metadata for property: " + property
            );
        }
    }

    @Test
    void metadataContainsEnumHintsForModeProperties() throws IOException {
        String metadata = readMetadata();

        assertHintValues(metadata, "hazelcast.toolkit.instance.mode", "CLIENT", "MEMBER", "NONE");
        assertHintValues(metadata, "hazelcast.toolkit.spring-cache.mode", "JCACHE", "NATIVE", "NONE");
        assertHintValues(metadata, "hazelcast.toolkit.hibernate.l2.region-factory",
                "JCACHE", "HAZELCAST_LOCAL", "HAZELCAST");
    }

    private static List<String> expectedProperties() {
        return List.of(
                "hazelcast.toolkit.cluster-name",
                "hazelcast.toolkit.enterprise-license-key",
                "hazelcast.toolkit.network.seed-members",
                "hazelcast.toolkit.compact.base-package",
                "hazelcast.toolkit.client.base-name",
                "hazelcast.toolkit.instance.mode",
                "hazelcast.toolkit.member.instance-name",
                "hazelcast.toolkit.member.cluster-name",
                "hazelcast.toolkit.member.network.port",
                "hazelcast.toolkit.member.network.port-auto-increment",
                "hazelcast.toolkit.member.network.public-address",
                "hazelcast.toolkit.member.network.join.auto-detection-enabled",
                "hazelcast.toolkit.member.network.join.multicast-enabled",
                "hazelcast.toolkit.member.network.join.tcp-ip-members",
                "hazelcast.toolkit.metrics.enabled",
                "hazelcast.toolkit.metrics.diagnostic-endpoint.enabled",
                "hazelcast.toolkit.health.enabled",
                "hazelcast.toolkit.actuator.near-cache-check.enabled",
                "hazelcast.toolkit.actuator.near-cache-check.entity-class",
                "hazelcast.toolkit.actuator.near-cache-check.entity-id",
                "hazelcast.toolkit.spring-cache.mode",
                "hazelcast.toolkit.hibernate.l2.enabled",
                "hazelcast.toolkit.hibernate.l2.region-factory",
                "hazelcast.toolkit.hibernate.l2.extended-config",
                "hazelcast.toolkit.hibernate.l2.use-query-cache",
                "hazelcast.toolkit.hibernate.l2.use-statistics"
        );
    }

    private static void assertHintValues(String metadata, String property, String... values) {
        int hintStart = metadata.indexOf("\"name\": \"" + property + "\"", metadata.indexOf("\"hints\""));
        assertTrue(hintStart >= 0, () -> "Missing hint block for property: " + property);

        for (String value : values) {
            Pattern pattern = Pattern.compile("\"value\"\\s*:\\s*\"" + Pattern.quote(value) + "\"");
            assertTrue(
                    pattern.matcher(metadata.substring(hintStart)).find(),
                    () -> "Missing hint value '" + value + "' for property: " + property
            );
        }
    }

    private static String readMetadata() throws IOException {
        try (InputStream stream = SpringConfigurationMetadataTest.class.getClassLoader()
                .getResourceAsStream("META-INF/spring-configuration-metadata.json")) {
            assertNotNull(stream, "spring-configuration-metadata.json must be packaged as a runtime resource");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
