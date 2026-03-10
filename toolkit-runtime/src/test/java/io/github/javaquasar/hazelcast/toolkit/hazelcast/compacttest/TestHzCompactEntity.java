package io.github.javaquasar.hazelcast.toolkit.hazelcast.compacttest;

import io.github.javaquasar.hazelcast.toolkit.annotation.HzCompact;

@HzCompact
public class TestHzCompactEntity {

    private String id;

    public TestHzCompactEntity() {
    }

    public TestHzCompactEntity(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
