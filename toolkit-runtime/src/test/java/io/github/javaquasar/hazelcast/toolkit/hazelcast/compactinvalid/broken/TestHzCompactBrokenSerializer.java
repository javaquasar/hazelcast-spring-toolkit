package io.github.javaquasar.hazelcast.toolkit.hazelcast.compactinvalid.broken;

import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import com.hazelcast.nio.serialization.compact.CompactWriter;

public class TestHzCompactBrokenSerializer implements CompactSerializer<TestHzCompactBrokenSerializerEntity> {

    public TestHzCompactBrokenSerializer(String ignored) {
    }

    @Override
    public TestHzCompactBrokenSerializerEntity read(CompactReader reader) {
        return new TestHzCompactBrokenSerializerEntity();
    }

    @Override
    public void write(CompactWriter writer, TestHzCompactBrokenSerializerEntity object) {
    }

    @Override
    public String getTypeName() {
        return "test-broken-serializer-entity";
    }

    @Override
    public Class<TestHzCompactBrokenSerializerEntity> getCompactClass() {
        return TestHzCompactBrokenSerializerEntity.class;
    }
}
