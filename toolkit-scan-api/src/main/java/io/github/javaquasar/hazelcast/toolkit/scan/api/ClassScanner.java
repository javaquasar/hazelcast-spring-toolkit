package io.github.javaquasar.hazelcast.toolkit.scan.api;

import java.lang.annotation.Annotation;
import java.util.Set;

/**
 * Pluggable classpath scanner.
 * Implementations may use Reflections, ClassGraph, or framework-specific mechanisms.
 */
public interface ClassScanner {

    /**
     * Finds classes annotated with {@code annotation} in the given package (and subpackages).
     */
    Set<Class<?>> findAnnotated(String basePackage, Class<? extends Annotation> annotation);

    /**
     * Finds subtypes (implementations/extends) of {@code superType} in the given package (and subpackages).
     */
    <T> Set<Class<? extends T>> findSubTypes(String basePackage, Class<T> superType);
}
