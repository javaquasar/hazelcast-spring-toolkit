package io.github.javaquasar.hazelcast.toolkit.hazelcast.compactinvalid.broken;

import io.github.javaquasar.hazelcast.toolkit.annotation.HzCompact;

@HzCompact(serializer = TestHzCompactBrokenSerializer.class)
public class TestHzCompactBrokenSerializerEntity {
}
