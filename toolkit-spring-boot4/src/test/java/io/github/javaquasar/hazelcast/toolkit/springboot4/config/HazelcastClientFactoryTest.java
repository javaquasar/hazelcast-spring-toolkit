package io.github.javaquasar.hazelcast.toolkit.springboot4.config;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.CompactSerializationConfig;
import com.hazelcast.spi.properties.ClusterProperty;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientFactory;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientNameBuilder;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.ReflectionsClassScanner;
import io.github.javaquasar.hazelcast.toolkit.springboot4.config.compact.invalid.BrokenCompactType;
import io.github.javaquasar.hazelcast.toolkit.springboot4.config.compact.valid.CustomizerCompactType;
import io.github.javaquasar.hazelcast.toolkit.springboot4.config.compact.valid.TestClientLimitEntryCache;
import io.github.javaquasar.hazelcast.toolkit.springboot4.config.compact.valid.TestReflectiveCompactType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HazelcastClientFactoryTest {

    @Test
    void createClientConfigRegistersReflectiveAndExplicitCompactTypesAndAppliesCustomizers() throws Exception {
        HazelcastClientFactory factory = new HazelcastClientFactory(
                new ReflectionsClassScanner(),
                List.of(clientConfig -> {
                    clientConfig.setProperty("test.customizer.applied", "true");
                    clientConfig.getSerializationConfig().getCompactSerializationConfig().addClass(CustomizerCompactType.class);
                })
        );

        ClientConfig clientConfig = factory.createClientConfig(
                "test-client",
                "test-cluster",
                List.of("127.0.0.1:5701"),
                true,
                "io.github.javaquasar.hazelcast.toolkit.springboot4.config.compact.valid"
        );
        CompactSerializationConfig compactConfig = clientConfig.getSerializationConfig().getCompactSerializationConfig();

        assertEquals("true", clientConfig.getProperty("test.customizer.applied"));
        assertTrue(registeredClasses(compactConfig).containsKey(TestReflectiveCompactType.class));
        assertTrue(registeredClasses(compactConfig).containsKey(TestClientLimitEntryCache.class));
        assertTrue(registeredClasses(compactConfig).containsKey(CustomizerCompactType.class));
        assertTrue(registeredTypeNames(compactConfig).containsKey("TestPlayerLimitEntryCache"));
    }

    @Test
    void buildClientNameReturnsBaseNameWhenApplicationNameMissing() {
        assertEquals("hz-client", HazelcastClientNameBuilder.build("hz.client", null));
        assertEquals("hz-client", HazelcastClientNameBuilder.build("hz.client", "   "));
    }

    @Test
    void buildClientNameAppendsSanitizedApplicationName() {
        assertEquals("hz-client-my-service",
                HazelcastClientNameBuilder.build("hz.client", "My Service"));
    }

    @Test
    void buildClientNameIgnoresApplicationNameThatSanitizesToBlank() {
        assertEquals("hz-client", HazelcastClientNameBuilder.build("hz.client", "!!!"));
    }

    @Test
    void buildClientNameUsesExplicitInstanceNameWhenNoBaseNameExists() {
        assertEquals("legacy-client", HazelcastClientNameBuilder.build(null, "legacy-client", "Billing Service"));
    }

    @Test
    void buildClientNameUsesApplicationNameWhenNoBaseNameOrExplicitInstanceNameExists() {
        assertEquals("billing-service", HazelcastClientNameBuilder.build(null, null, "Billing Service"));
    }

    @Test
    void buildClientNameGeneratesUniqueFallbackWhenNoNamingInputsExist() {
        String first = HazelcastClientNameBuilder.build(null, null, null);
        String second = HazelcastClientNameBuilder.build(null, null, null);

        assertTrue(first.startsWith("hz-client-"));
        assertTrue(second.startsWith("hz-client-"));
        assertNotEquals(first, second);
    }

    @Test
    void createClientConfigUsesSanitizedApplicationNameInInstanceName() {
        HazelcastClientFactory factory = new HazelcastClientFactory(new ReflectionsClassScanner());

        ClientConfig clientConfig = factory.createClientConfig(
                "hz.client",
                " Billing/API @ EU ",
                "test-cluster",
                List.of("127.0.0.1:5701"),
                true,
                null
        );

        assertEquals("hz-client-billing-api-eu", clientConfig.getInstanceName());
    }

    @Test
    void createClientConfigUsesExplicitInstanceNameWhenNoBaseNameExists() {
        HazelcastClientFactory factory = new HazelcastClientFactory(new ReflectionsClassScanner());

        ClientConfig clientConfig = factory.createClientConfig(
                null,
                "legacy-client",
                " Billing/API @ EU ",
                "test-cluster",
                List.of("127.0.0.1:5701"),
                true,
                null
        );

        assertEquals("legacy-client", clientConfig.getInstanceName());
    }

    @Test
    void createClientConfigUsesApplicationNameWhenNoBaseNameOrExplicitInstanceNameExists() {
        HazelcastClientFactory factory = new HazelcastClientFactory(new ReflectionsClassScanner());

        ClientConfig clientConfig = factory.createClientConfig(
                null,
                null,
                " Billing/API @ EU ",
                "test-cluster",
                List.of("127.0.0.1:5701"),
                true,
                null
        );

        assertEquals("billing-api-eu", clientConfig.getInstanceName());
    }

    @Test
    void createClientConfigGeneratesFallbackWhenNoNamingInputsExist() {
        HazelcastClientFactory factory = new HazelcastClientFactory(new ReflectionsClassScanner());

        ClientConfig clientConfig = factory.createClientConfig(
                null,
                null,
                null,
                "test-cluster",
                List.of("127.0.0.1:5701"),
                true,
                null
        );

        assertTrue(clientConfig.getInstanceName().startsWith("hz-client-"));
    }

    @Test
    void createClientConfigCustomizersCanApplyEnterpriseLicenseKeyProperty() {
        HazelcastClientFactory factory = new HazelcastClientFactory(
                new ReflectionsClassScanner(),
                List.of(clientConfig -> clientConfig.setProperty(ClusterProperty.ENTERPRISE_LICENSE_KEY.getName(), "enterprise-key"))
        );

        ClientConfig clientConfig = factory.createClientConfig(
                "hz.client",
                "billing-service",
                "test-cluster",
                List.of("127.0.0.1:5701"),
                true,
                null
        );

        assertEquals("enterprise-key", clientConfig.getProperty(ClusterProperty.ENTERPRISE_LICENSE_KEY.getName()));
    }

    @Test
    void createClientConfigRejectsMismatchedExplicitSerializer() {
        HazelcastClientFactory factory = new HazelcastClientFactory(new ReflectionsClassScanner());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> factory.createClientConfig(
                        "test-client",
                        "test-cluster",
                        List.of("127.0.0.1:5701"),
                        true,
                        "io.github.javaquasar.hazelcast.toolkit.springboot4.config.compact.invalid"
                )
        );

        assertTrue(exception.getMessage().contains(BrokenCompactType.class.getName()));
    }

    @SuppressWarnings("unchecked")
    private Map<Class<?>, ?> registeredClasses(CompactSerializationConfig compactConfig) throws Exception {
        Field field = CompactSerializationConfig.class.getDeclaredField("classToRegistration");
        field.setAccessible(true);
        return (Map<Class<?>, ?>) field.get(compactConfig);
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> registeredTypeNames(CompactSerializationConfig compactConfig) throws Exception {
        Field field = CompactSerializationConfig.class.getDeclaredField("typeNameToRegistration");
        field.setAccessible(true);
        return (Map<String, ?>) field.get(compactConfig);
    }
}
