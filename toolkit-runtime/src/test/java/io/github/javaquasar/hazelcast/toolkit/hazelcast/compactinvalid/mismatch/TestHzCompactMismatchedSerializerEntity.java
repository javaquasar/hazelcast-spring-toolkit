package io.github.javaquasar.hazelcast.toolkit.hazelcast.compactinvalid.mismatch;

import io.github.javaquasar.hazelcast.toolkit.annotation.HzCompact;

@HzCompact(serializer = TestHzCompactMismatchedSerializer.class)
public class TestHzCompactMismatchedSerializerEntity {
}
