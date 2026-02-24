package io.github.javaquasar.hazelcast.toolkit.scan.reflections;

import io.github.javaquasar.hazelcast.toolkit.scan.api.ClassScanner;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.lang.annotation.Annotation;
import java.util.Set;

/**
 * JVM-friendly scanner based on org.reflections.
 */
public class ReflectionsClassScanner implements ClassScanner {

    @Override
    public Set<Class<?>> findAnnotated(String basePackage, Class<? extends Annotation> annotation) {
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .forPackage(basePackage)
                .setScanners(Scanners.TypesAnnotated));

        return reflections.getTypesAnnotatedWith(annotation);
    }

    @Override
    public <T> Set<Class<? extends T>> findSubTypes(String basePackage, Class<T> superType) {
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .forPackage(basePackage)
                .setScanners(Scanners.SubTypes));

        return reflections.getSubTypesOf(superType);
    }
}
