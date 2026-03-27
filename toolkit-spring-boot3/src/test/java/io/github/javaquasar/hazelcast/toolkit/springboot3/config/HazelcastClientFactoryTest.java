package io.github.javaquasar.hazelcast.toolkit.springboot3.config;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.CompactSerializationConfig;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientFactory;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientNameBuilder;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.compat.CompactClassesScanner;
import io.github.javaquasar.hazelcast.toolkit.springboot3.config.compact.invalid.BrokenCompactType;
import io.github.javaquasar.hazelcast.toolkit.springboot3.config.compact.valid.CustomizerCompactType;
import io.github.javaquasar.hazelcast.toolkit.springboot3.config.compact.valid.TestClientLimitEntryCache;
import io.github.javaquasar.hazelcast.toolkit.springboot3.config.compact.valid.TestReflectiveCompactType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HazelcastClientFactoryTest {

    @Test
    void createClientConfigRegistersReflectiveAndExplicitCompactTypesAndAppliesCustomizers() throws Exception {
        HazelcastClientFactory factory = new HazelcastClientFactory(
                new CompactClassesScanner(),
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
                "io.github.javaquasar.hazelcast.toolkit.springboot3.config.compact.valid"
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
    void createClientConfigUsesSanitizedApplicationNameInInstanceName() {
        HazelcastClientFactory factory = new HazelcastClientFactory(new CompactClassesScanner());

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
    void createClientConfigRejectsMismatchedExplicitSerializer() {
        HazelcastClientFactory factory = new HazelcastClientFactory(new CompactClassesScanner());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> factory.createClientConfig(
                        "test-client",
                        "test-cluster",
                        List.of("127.0.0.1:5701"),
                        true,
                        "io.github.javaquasar.hazelcast.toolkit.springboot3.config.compact.invalid"
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
