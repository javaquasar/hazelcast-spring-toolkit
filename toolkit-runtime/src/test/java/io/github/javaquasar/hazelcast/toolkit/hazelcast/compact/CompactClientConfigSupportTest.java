package io.github.javaquasar.hazelcast.toolkit.hazelcast.compact;

import com.hazelcast.config.CompactSerializationConfig;
import com.hazelcast.config.SerializationConfig;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.compacttest.TestHzCompactEntity;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.compacttest.TestHzCompactExplicitEntity;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.compactinvalid.broken.TestHzCompactBrokenSerializerEntity;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.compactinvalid.mismatch.TestHzCompactMismatchedSerializerEntity;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.ReflectionsClassScanner;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactClientConfigSupportTest {

    @Test
    void registersReflectiveCompactClassesAndExplicitSerializers() throws Exception {
        SerializationConfig serializationConfig = new SerializationConfig();

        new CompactClientConfigSupport(new ReflectionsClassScanner())
                .registerCompactTypes(serializationConfig, "io.github.javaquasar.hazelcast.toolkit.hazelcast.compacttest");

        CompactSerializationConfig compactConfig = serializationConfig.getCompactSerializationConfig();
        Map<Class<?>, ?> registeredTypes = registeredCompactClasses(compactConfig);

        assertEquals(2, registeredTypes.size());
        assertTrue(registeredTypes.containsKey(TestHzCompactEntity.class));
        assertTrue(registeredTypes.containsKey(TestHzCompactExplicitEntity.class));
    }

    @Test
    void failsWhenExplicitSerializerHasNoNoArgsConstructor() {
        SerializationConfig serializationConfig = new SerializationConfig();

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> new CompactClientConfigSupport(new ReflectionsClassScanner())
                        .registerCompactTypes(
                                serializationConfig,
                                TestHzCompactBrokenSerializerEntity.class.getPackageName()
                        )
        );

        assertTrue(ex.getMessage().contains("Failed to instantiate CompactSerializer"));
        assertTrue(ex.getMessage().contains("Make sure it has a no-args constructor."));
    }

    @Test
    void failsWhenExplicitSerializerReturnsDifferentCompactClass() {
        SerializationConfig serializationConfig = new SerializationConfig();

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> new CompactClientConfigSupport(new ReflectionsClassScanner())
                        .registerCompactTypes(
                                serializationConfig,
                                TestHzCompactMismatchedSerializerEntity.class.getPackageName()
                        )
        );

        assertTrue(ex.getMessage().contains("is declared on"));
        assertTrue(ex.getMessage().contains("but getCompactClass() returns"));
    }

    @SuppressWarnings("unchecked")
    private Map<Class<?>, ?> registeredCompactClasses(CompactSerializationConfig compactConfig) throws Exception {
        Field field = CompactSerializationConfig.class.getDeclaredField("classToRegistration");
        field.setAccessible(true);
        return (Map<Class<?>, ?>) field.get(compactConfig);
    }
}
