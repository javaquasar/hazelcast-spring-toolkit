package io.github.javaquasar.hazelcast.toolkit.hazelcast;

import com.hazelcast.config.CompactSerializationConfig;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.compacttest.TestHzCompactEntity;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.ReflectionsClassScanner;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactRegistrationTest {

    @Test
    void registersHzCompactAnnotatedClass() throws Exception {
        CompactSerializationConfig compactConfig = new CompactSerializationConfig();

        int registered = CompactRegistration.registerAnnotated(
                compactConfig,
                new ReflectionsClassScanner(),
                "io.github.javaquasar.hazelcast.toolkit.hazelcast.compacttest"
        );

        assertEquals(1, registered);
        assertTrue(registeredCompactClasses(compactConfig).containsKey(TestHzCompactEntity.class));
    }

    @SuppressWarnings("unchecked")
    private Map<Class<?>, ?> registeredCompactClasses(CompactSerializationConfig compactConfig) throws Exception {
        Field field = CompactSerializationConfig.class.getDeclaredField("classToRegistration");
        field.setAccessible(true);
        return (Map<Class<?>, ?>) field.get(compactConfig);
    }
}
