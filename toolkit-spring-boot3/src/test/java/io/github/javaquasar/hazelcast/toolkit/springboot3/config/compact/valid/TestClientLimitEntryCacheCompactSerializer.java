package io.github.javaquasar.hazelcast.toolkit.springboot3.config.compact.valid;

import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import com.hazelcast.nio.serialization.compact.CompactWriter;

public class TestClientLimitEntryCacheCompactSerializer implements CompactSerializer<TestClientLimitEntryCache> {

    @Override
    public void write(CompactWriter writer, TestClientLimitEntryCache object) {
        writer.writeInt32("periodId", object.getPeriod() != null ? object.getPeriod().getId() : 0);
        writer.writeInt32("limitAmount", nvl(object.getLimitAmount()));
        writer.writeInt32("counter", nvl(object.getCounter()));
        writer.writeInt32("previousCounterValue", nvl(object.getPreviousCounterValue()));
        writer.writeBoolean("limitReached", Boolean.TRUE.equals(object.getLimitReached()));
        writer.writeInt64("startMillis", nvl(object.getStartMillis()));
        writer.writeInt64("thisSessionStartMillis", nvl(object.getThisSessionStartMillis()));
    }

    @Override
    public TestClientLimitEntryCache read(CompactReader reader) {
        TestClientLimitEntryCache object = new TestClientLimitEntryCache();

        int periodId = reader.readInt32("periodId");
        object.setPeriod(periodId == 0 ? null : TestPeriod.fromId(periodId));
        object.setLimitAmount(reader.readInt32("limitAmount"));
        object.setCounter(reader.readInt32("counter"));
        object.setPreviousCounterValue(reader.readInt32("previousCounterValue"));
        object.setLimitReached(reader.readBoolean("limitReached"));
        object.setStartMillis(reader.readInt64("startMillis"));
        object.setThisSessionStartMillis(reader.readInt64("thisSessionStartMillis"));
        return object;
    }

    @Override
    public String getTypeName() {
        return "TestPlayerLimitEntryCache";
    }

    @Override
    public Class<TestClientLimitEntryCache> getCompactClass() {
        return TestClientLimitEntryCache.class;
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private static long nvl(Long value) {
        return value == null ? 0L : value;
    }
}
