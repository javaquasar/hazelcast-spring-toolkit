package io.github.javaquasar.hazelcast.toolkit.hazelcast.compact;

import com.hazelcast.config.CompactSerializationConfig;
import com.hazelcast.config.SerializationConfig;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.compat.CompactClassesScanner;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.compat.CompactScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies {@code @HzCompact} scanning results to Hazelcast compact serialization config.
 */
public class CompactClientConfigSupport {

    private static final Logger log = LoggerFactory.getLogger(CompactClientConfigSupport.class);

    private final CompactClassesScanner compactScanner;

    public CompactClientConfigSupport(CompactClassesScanner compactScanner) {
        this.compactScanner = compactScanner;
    }

    public void registerCompactTypes(SerializationConfig serializationConfig, String compactBasePackage) {
        if (compactBasePackage == null || compactBasePackage.isBlank()) {
            return;
        }

        CompactSerializationConfig compact = serializationConfig.getCompactSerializationConfig();
        CompactScanResult scanResult = compactScanner.scan(compactBasePackage);

        scanResult.serializers().forEach(compact::addSerializer);
        scanResult.compactClasses().forEach(compact::addClass);

        log.info(
                "Registered {} @HzCompact types from basePackage={} (serializers={}, reflectiveClasses={})",
                scanResult.serializers().size() + scanResult.compactClasses().size(),
                compactBasePackage,
                scanResult.serializers().size(),
                scanResult.compactClasses().size()
        );
    }
}
