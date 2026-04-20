package io.github.javaquasar.hazelcast.toolkit.hazelcast.compacttest;

import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import com.hazelcast.nio.serialization.compact.CompactWriter;

public class TestHzCompactExplicitEntitySerializer implements CompactSerializer<TestHzCompactExplicitEntity> {

    @Override
    public TestHzCompactExplicitEntity read(CompactReader reader) {
        return new TestHzCompactExplicitEntity(reader.readString("id"));
    }

    @Override
    public void write(CompactWriter writer, TestHzCompactExplicitEntity object) {
        writer.writeString("id", object.getId());
    }

    @Override
    public String getTypeName() {
        return "test-explicit-entity";
    }

    @Override
    public Class<TestHzCompactExplicitEntity> getCompactClass() {
        return TestHzCompactExplicitEntity.class;
    }
}
