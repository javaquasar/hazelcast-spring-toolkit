package io.github.javaquasar.hazelcast.toolkit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an {@code EntryListener} (or similar) bean for automatic registration.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HzIMapListener {
    /** IMap name */
    String map();

    boolean includeValue() default true;

    boolean localOnly() default false;
}
