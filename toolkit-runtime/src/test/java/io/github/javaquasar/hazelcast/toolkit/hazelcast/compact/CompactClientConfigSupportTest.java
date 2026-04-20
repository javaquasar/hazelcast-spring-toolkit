package io.github.javaquasar.hazelcast.toolkit.hazelcast.compact;

import com.hazelcast.config.CompactSerializationConfig;
import com.hazelcast.config.SerializationConfig;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.compacttest.TestHzCompactEntity;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.compacttest.TestHzCompactExplicitEntity;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.compat.CompactClassesScanner;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactClientConfigSupportTest {

    @Test
    void registersReflectiveCompactClassesAndExplicitSerializers() throws Exception {
        SerializationConfig serializationConfig = new SerializationConfig();

        new CompactClientConfigSupport(new CompactClassesScanner())
                .registerCompactTypes(serializationConfig, "io.github.javaquasar.hazelcast.toolkit.hazelcast.compacttest");

        CompactSerializationConfig compactConfig = serializationConfig.getCompactSerializationConfig();
        Map<Class<?>, ?> registeredTypes = registeredCompactClasses(compactConfig);

        assertEquals(2, registeredTypes.size());
        assertTrue(registeredTypes.containsKey(TestHzCompactEntity.class));
        assertTrue(registeredTypes.containsKey(TestHzCompactExplicitEntity.class));
    }

    @SuppressWarnings("unchecked")
    private Map<Class<?>, ?> registeredCompactClasses(CompactSerializationConfig compactConfig) throws Exception {
        Field field = CompactSerializationConfig.class.getDeclaredField("classToRegistration");
        field.setAccessible(true);
        return (Map<Class<?>, ?>) field.get(compactConfig);
    }
}
