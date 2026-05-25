package io.github.javaquasar.hazelcast.toolkit.hazelcast.compactinvalid.mismatch;

import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import com.hazelcast.nio.serialization.compact.CompactWriter;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.compacttest.TestHzCompactEntity;

public class TestHzCompactMismatchedSerializer implements CompactSerializer<TestHzCompactEntity> {

    @Override
    public TestHzCompactEntity read(CompactReader reader) {
        return new TestHzCompactEntity(reader.readString("id"));
    }

    @Override
    public void write(CompactWriter writer, TestHzCompactEntity object) {
        writer.writeString("id", object.getId());
    }

    @Override
    public String getTypeName() {
        return "test-mismatched-serializer-entity";
    }

    @Override
    public Class<TestHzCompactEntity> getCompactClass() {
        return TestHzCompactEntity.class;
    }
}
