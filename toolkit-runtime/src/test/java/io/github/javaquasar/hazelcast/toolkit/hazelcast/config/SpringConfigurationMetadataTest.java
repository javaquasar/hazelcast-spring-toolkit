package io.github.javaquasar.hazelcast.toolkit.hazelcast.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringConfigurationMetadataTest {

    @Test
    void metadataContainsKeyToolkitProperties() throws IOException {
        String metadata = readMetadata();

        assertTrue(metadata.contains("\"hazelcast.toolkit.metrics.enabled\""));
        assertTrue(metadata.contains("\"hazelcast.toolkit.metrics.diagnostic-endpoint.enabled\""));
        assertTrue(metadata.contains("\"hazelcast.toolkit.actuator.near-cache-check.enabled\""));
        assertTrue(metadata.contains("\"hazelcast.toolkit.spring-cache.mode\""));
        assertTrue(metadata.contains("\"hazelcast.toolkit.hibernate.l2.region-factory\""));
    }

    private static String readMetadata() throws IOException {
        try (InputStream stream = SpringConfigurationMetadataTest.class.getClassLoader()
                .getResourceAsStream("META-INF/spring-configuration-metadata.json")) {
            assertNotNull(stream, "spring-configuration-metadata.json must be packaged as a runtime resource");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
