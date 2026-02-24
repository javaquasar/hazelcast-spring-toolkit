package io.github.javaquasar.hazelcast.toolkit.hazelcast;

import com.hazelcast.config.CompactSerializationConfig;
import io.github.javaquasar.hazelcast.toolkit.annotation.HzCompact;
import io.github.javaquasar.hazelcast.toolkit.scan.api.ClassScanner;

import java.util.Set;

/**
 * Registers {@link HzCompact}-annotated classes into Hazelcast Compact Serialization.
 */
public final class CompactRegistration {

    private CompactRegistration() {
    }

    public static int registerAnnotated(CompactSerializationConfig compactConfig,
                                        ClassScanner scanner,
                                        String basePackage) {
        Set<Class<?>> types = scanner.findAnnotated(basePackage, HzCompact.class);
        types.forEach(compactConfig::addClass);
        return types.size();
    }
}
