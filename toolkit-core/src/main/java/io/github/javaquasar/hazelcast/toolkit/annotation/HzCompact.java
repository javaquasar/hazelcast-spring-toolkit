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
 * Marks a class as eligible for Hazelcast Compact serialization and registers it
 * automatically when the toolkit scans the configured base package.
 *
 * <p>Hazelcast Compact serialization is a schema-based binary format that is more
 * efficient than Java serialization and does not require the serialized class to
 * implement {@link java.io.Serializable}.  This annotation supports two registration
 * modes:
 *
 * <p><b>Zero-config (reflective) mode</b> — omit the {@link #serializer()} attribute.
 * Hazelcast infers the schema from the class fields at runtime using reflection.
 * Suitable for simple value types with no custom encoding requirements.
 * <pre>{@code
 * @HzCompact
 * public class UserProfile {
 *     private String userId;
 *     private String nickname;
 *     // getters + setters ...
 * }
 * }</pre>
 *
 * <p><b>Explicit serializer mode</b> — provide a {@link CompactSerializer} implementation
 * via {@link #serializer()}.  Use this mode when the binary schema must be controlled
 * explicitly (e.g. enum encoding, versioning, cross-language compatibility).
 * <pre>{@code
 * @HzCompact(serializer = OrderEntryCompactSerializer.class)
 * public class OrderEntry {
 *     // fields ...
 * }
 * }</pre>
 *
 * <p>Scanning and registration are performed by
 * {@code CompactClassesScanner} and {@code CompactClientConfigSupport} in
 * {@code toolkit-scan-reflections} and {@code toolkit-runtime} respectively.
 * Explicit serializers are registered before reflective classes to match
 * Hazelcast's recommended registration order.
 *
 * @see com.hazelcast.nio.serialization.compact.CompactSerializer
 * @since 0.1.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HzCompact {

    /**
     * Optional explicit {@link CompactSerializer} for this class.
     *
     * <p>When specified, the toolkit instantiates the serializer using its
     * no-argument constructor and validates that
     * {@code serializer.getCompactClass() == annotatedClass}.  A mismatch
     * causes an {@link IllegalStateException} at application startup.
     *
     * <p>When omitted (or left at the default {@link NoopCompactSerializer}),
     * the class is registered for zero-config reflective compact serialization —
     * Hazelcast derives the schema from the class fields automatically.
     *
     * <p><b>Requirement:</b> the serializer class must have a public no-argument
     * constructor.
     */
    Class<? extends CompactSerializer<?>> serializer() default NoopCompactSerializer.class;

    /**
     * Internal sentinel value used to represent the absence of an explicit serializer.
     *
     * <p>This class is never instantiated or invoked by the toolkit.  It exists solely
     * as the annotation default so that {@code serializer() == NoopCompactSerializer.class}
     * can be used as an absent-value check without resorting to {@code null}.
     *
     * <p><b>Do not reference this class directly in application code.</b>
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
