package io.github.javaquasar.hazelcast.toolkit.scan.reflections.compat;

import com.hazelcast.nio.serialization.compact.CompactSerializer;
import io.github.javaquasar.hazelcast.toolkit.scan.api.ClassScanner;

import java.util.Set;

/**
 * Scan result for Compact serialization registration.
 *
 * @deprecated use {@link ClassScanner} together with
 * {@code io.github.javaquasar.hazelcast.toolkit.hazelcast.compact.CompactClientConfigSupport}.
 * This record is retained only for callers of the legacy
 * {@link CompactClassesScanner} compatibility API.
 */
@Deprecated(since = "0.9.0", forRemoval = false)
public record CompactScanResult(
        Set<Class<?>> compactClasses,
        Set<CompactSerializer<?>> serializers
) {
}
