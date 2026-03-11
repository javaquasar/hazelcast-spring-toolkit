package io.github.javaquasar.hazelcast.toolkit.springboot3.config.compact.valid;

import io.github.javaquasar.hazelcast.toolkit.annotation.HzCompact;

@HzCompact
public class TestReflectiveCompactType {

    private String value;

    public TestReflectiveCompactType() {
    }

    public TestReflectiveCompactType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
