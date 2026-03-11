package io.github.javaquasar.hazelcast.toolkit.scan.reflections.compat;

import com.hazelcast.nio.serialization.compact.CompactSerializer;

import java.util.Set;

/**
 * Scan result for Compact serialization registration.
 */
public record CompactScanResult(
        Set<Class<?>> compactClasses,
        Set<CompactSerializer<?>> serializers
) {
}
