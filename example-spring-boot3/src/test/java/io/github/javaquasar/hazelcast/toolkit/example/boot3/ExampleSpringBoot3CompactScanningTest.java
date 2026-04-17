package io.github.javaquasar.hazelcast.toolkit.example.boot3;

import com.hazelcast.nio.serialization.compact.CompactSerializer;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookCacheEntry;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookCacheEntryCompactSerializer;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookRecommendation;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.compat.CompactClassesScanner;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.compat.CompactScanResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExampleSpringBoot3CompactScanningTest {

    @Test
    void scansReflectiveAndExplicitCompactRegistrations() {
        CompactScanResult result = new CompactClassesScanner()
                .scan("io.github.javaquasar.hazelcast.toolkit.example.boot3.model");

        assertThat(result.compactClasses()).contains(ExampleBookRecommendation.class);

        assertThat(result.serializers())
                .extracting(CompactSerializer::getClass)
                .contains(ExampleBookCacheEntryCompactSerializer.class);

        assertThat(result.serializers())
                .extracting(CompactSerializer::getCompactClass)
                .anyMatch(ExampleBookCacheEntry.class::equals);
    }
}
