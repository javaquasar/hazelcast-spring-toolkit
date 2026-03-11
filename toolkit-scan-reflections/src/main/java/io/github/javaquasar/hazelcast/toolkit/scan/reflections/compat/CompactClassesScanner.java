package io.github.javaquasar.hazelcast.toolkit.scan.reflections.compat;

import com.hazelcast.nio.serialization.compact.CompactSerializer;
import io.github.javaquasar.hazelcast.toolkit.annotation.HzCompact;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Set;

/**
 * Backwards-compatible scanner: finds classes annotated with {@link HzCompact}.
 */
public class CompactClassesScanner {

    public CompactScanResult scan(String basePackage) {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> annotated = reflections.getTypesAnnotatedWith(HzCompact.class);

        Set<Class<?>> compactClasses = new HashSet<>();
        Set<CompactSerializer<?>> serializers = new HashSet<>();

        for (Class<?> compactClass : annotated) {
            HzCompact annotation = compactClass.getAnnotation(HzCompact.class);
            Class<? extends CompactSerializer<?>> serializerClass = annotation.serializer();

            if (HzCompact.NoopCompactSerializer.class.equals(serializerClass)) {
                compactClasses.add(compactClass);
                continue;
            }

            CompactSerializer<?> serializer = instantiate(serializerClass);
            validateSerializer(serializerClass, serializer, compactClass);
            serializers.add(serializer);
        }

        return new CompactScanResult(compactClasses, serializers);
    }

    public Set<Class<?>> scanCompactSchemas(String basePackage) {
        return scan(basePackage).compactClasses();
    }

    private static CompactSerializer<?> instantiate(Class<? extends CompactSerializer<?>> serializerClass) {
        try {
            Constructor<? extends CompactSerializer<?>> ctor = serializerClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to instantiate CompactSerializer: " + serializerClass.getName()
                            + ". Make sure it has a no-args constructor.",
                    e
            );
        }
    }

    private static void validateSerializer(Class<? extends CompactSerializer<?>> serializerClass,
                                           CompactSerializer<?> serializer,
                                           Class<?> compactClass) {
        if (!compactClass.equals(serializer.getCompactClass())) {
            throw new IllegalStateException(
                    "CompactSerializer " + serializerClass.getName()
                            + " is declared on " + compactClass.getName()
                            + " but getCompactClass() returns " + serializer.getCompactClass().getName()
            );
        }
    }
}
