package io.github.javaquasar.hazelcast.toolkit.springboot3.config.compact.invalid;

import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import com.hazelcast.nio.serialization.compact.CompactWriter;

public class WrongCompactSerializer implements CompactSerializer<UnexpectedCompactType> {

    @Override
    public void write(CompactWriter writer, UnexpectedCompactType object) {
        writer.writeString("value", object.getValue());
    }

    @Override
    public UnexpectedCompactType read(CompactReader reader) {
        UnexpectedCompactType object = new UnexpectedCompactType();
        object.setValue(reader.readString("value"));
        return object;
    }

    @Override
    public String getTypeName() {
        return "UnexpectedCompactType";
    }

    @Override
    public Class<UnexpectedCompactType> getCompactClass() {
        return UnexpectedCompactType.class;
    }
}
