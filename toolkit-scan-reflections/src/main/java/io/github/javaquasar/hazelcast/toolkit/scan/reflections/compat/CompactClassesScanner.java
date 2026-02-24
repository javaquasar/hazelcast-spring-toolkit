package io.github.javaquasar.hazelcast.toolkit.scan.reflections.compat;

import io.github.javaquasar.hazelcast.toolkit.annotation.HzCompact;
import org.reflections.Reflections;

import java.util.Set;

/**
 * Backwards-compatible scanner: finds classes annotated with {@link HzCompact}.
 */
public class CompactClassesScanner {

    public Set<Class<?>> scanCompactSchemas(String basePackage) {
        Reflections reflections = new Reflections(basePackage);
        return reflections.getTypesAnnotatedWith(HzCompact.class);
    }
}
