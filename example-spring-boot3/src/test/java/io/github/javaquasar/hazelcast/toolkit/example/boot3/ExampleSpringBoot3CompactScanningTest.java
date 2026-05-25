package io.github.javaquasar.hazelcast.toolkit.example.boot3;

import com.hazelcast.config.CompactSerializationConfig;
import com.hazelcast.config.SerializationConfig;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookCacheEntry;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookCacheEntryCompactSerializer;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookRecommendation;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.compact.CompactClientConfigSupport;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.ReflectionsClassScanner;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExampleSpringBoot3CompactScanningTest {

    @Test
    void scansReflectiveAndExplicitCompactRegistrations() throws Exception {
        SerializationConfig serializationConfig = new SerializationConfig();

        new CompactClientConfigSupport(new ReflectionsClassScanner())
                .registerCompactTypes(serializationConfig, "io.github.javaquasar.hazelcast.toolkit.example.boot3.model");

        Map<Class<?>, Object> registeredTypes = registeredCompactClasses(
                serializationConfig.getCompactSerializationConfig()
        );

        assertThat(registeredTypes).containsKeys(ExampleBookRecommendation.class, ExampleBookCacheEntry.class);

        Object explicitRegistration = registeredTypes.get(ExampleBookCacheEntry.class);
        CompactSerializer<?> serializer = registeredSerializer(explicitRegistration);

        assertThat(serializer).isInstanceOf(ExampleBookCacheEntryCompactSerializer.class);
        assertThat(serializer.getCompactClass()).isEqualTo(ExampleBookCacheEntry.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<Class<?>, Object> registeredCompactClasses(CompactSerializationConfig compactConfig)
            throws Exception {
        Field field = CompactSerializationConfig.class.getDeclaredField("classToRegistration");
        field.setAccessible(true);
        return (Map<Class<?>, Object>) field.get(compactConfig);
    }

    private static CompactSerializer<?> registeredSerializer(Object explicitRegistration) throws Exception {
        Field field = explicitRegistration.getClass().getDeclaredField("element3");
        field.setAccessible(true);
        return (CompactSerializer<?>) field.get(explicitRegistration);
    }
}
