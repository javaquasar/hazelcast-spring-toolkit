package io.github.javaquasar.hazelcast.toolkit.annotation;

import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import com.hazelcast.nio.serialization.compact.CompactWriter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as eligible for Hazelcast Compact serialization.
 *
 * <p>If no serializer is provided, Hazelcast zero-config reflective compact
 * serialization is used.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HzCompact {

    /**
     * Explicit CompactSerializer for this class.
     *
     * <p>If not specified, the class will be registered using zero-config
     * reflective compact serialization.</p>
     */
    Class<? extends CompactSerializer<?>> serializer() default NoopCompactSerializer.class;

    /**
     * Marker serializer used to indicate that no explicit serializer is provided.
     * This class must never be actually instantiated or used.
     */
    final class NoopCompactSerializer implements CompactSerializer<Object> {

        @Override
        public Object read(CompactReader reader) {
            throw new UnsupportedOperationException("Noop serializer should never be used");
        }

        @Override
        public void write(CompactWriter writer, Object object) {
            throw new UnsupportedOperationException("Noop serializer should never be used");
        }

        @Override
        public String getTypeName() {
            return "noop";
        }

        @Override
        public Class<Object> getCompactClass() {
            return Object.class;
        }
    }
}
