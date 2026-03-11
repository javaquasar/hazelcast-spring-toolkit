package io.github.javaquasar.hazelcast.toolkit.springboot3.config.compact.invalid;

import io.github.javaquasar.hazelcast.toolkit.annotation.HzCompact;

@HzCompact(serializer = WrongCompactSerializer.class)
public class BrokenCompactType {

    private String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
