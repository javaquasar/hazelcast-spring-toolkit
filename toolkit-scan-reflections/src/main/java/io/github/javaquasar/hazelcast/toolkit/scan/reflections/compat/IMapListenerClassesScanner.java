package io.github.javaquasar.hazelcast.toolkit.scan.reflections.compat;

import io.github.javaquasar.hazelcast.toolkit.annotation.HzIMapListener;
import org.reflections.Reflections;

import java.util.Set;

/**
 * Backwards-compatible scanner: finds classes annotated with {@link HzIMapListener}.
 */
public class IMapListenerClassesScanner {

    public Set<Class<?>> scanIMapListeners(String basePackage) {
        Reflections reflections = new Reflections(basePackage);
        return reflections.getTypesAnnotatedWith(HzIMapListener.class);
    }
}
