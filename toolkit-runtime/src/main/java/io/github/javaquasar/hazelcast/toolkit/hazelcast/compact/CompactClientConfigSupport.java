package io.github.javaquasar.hazelcast.toolkit.hazelcast.compact;

import com.hazelcast.config.CompactSerializationConfig;
import com.hazelcast.config.SerializationConfig;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import io.github.javaquasar.hazelcast.toolkit.annotation.HzCompact;
import io.github.javaquasar.hazelcast.toolkit.scan.api.ClassScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Set;

/**
 * Applies {@code @HzCompact} scanning results to Hazelcast compact serialization config.
 */
public class CompactClientConfigSupport {

    private static final Logger log = LoggerFactory.getLogger(CompactClientConfigSupport.class);

    private final ClassScanner classScanner;

    public CompactClientConfigSupport(ClassScanner classScanner) {
        this.classScanner = classScanner;
    }

    public void registerCompactTypes(SerializationConfig serializationConfig, String compactBasePackage) {
        if (compactBasePackage == null || compactBasePackage.isBlank()) {
            return;
        }

        CompactSerializationConfig compact = serializationConfig.getCompactSerializationConfig();
        Set<Class<?>> compactClasses = new HashSet<>();
        Set<CompactSerializer<?>> serializers = new HashSet<>();

        for (Class<?> compactClass : classScanner.findAnnotated(compactBasePackage, HzCompact.class)) {
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

        serializers.forEach(compact::addSerializer);
        compactClasses.forEach(compact::addClass);

        log.info(
                "Registered {} @HzCompact types from basePackage={} (serializers={}, reflectiveClasses={})",
                serializers.size() + compactClasses.size(),
                compactBasePackage,
                serializers.size(),
                compactClasses.size()
        );
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
