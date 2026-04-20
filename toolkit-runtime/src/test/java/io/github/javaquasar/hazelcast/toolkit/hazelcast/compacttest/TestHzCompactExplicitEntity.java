package io.github.javaquasar.hazelcast.toolkit.hazelcast.compacttest;

import io.github.javaquasar.hazelcast.toolkit.annotation.HzCompact;

@HzCompact(serializer = TestHzCompactExplicitEntitySerializer.class)
public class TestHzCompactExplicitEntity {

    private String id;

    protected TestHzCompactExplicitEntity() {
    }

    public TestHzCompactExplicitEntity(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
