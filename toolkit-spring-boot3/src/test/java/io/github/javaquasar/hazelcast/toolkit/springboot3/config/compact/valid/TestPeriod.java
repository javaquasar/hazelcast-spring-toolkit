package io.github.javaquasar.hazelcast.toolkit.springboot3.config.compact.valid;

public enum TestPeriod {
    DAILY(1),
    WEEKLY(2),
    MONTHLY(3),
    PER_SESSION(4);

    private final int id;

    TestPeriod(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static TestPeriod fromId(int id) {
        for (TestPeriod value : values()) {
            if (value.id == id) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown id: " + id);
    }
}
