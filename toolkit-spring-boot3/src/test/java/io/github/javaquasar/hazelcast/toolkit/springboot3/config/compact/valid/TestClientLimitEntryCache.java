package io.github.javaquasar.hazelcast.toolkit.springboot3.config.compact.valid;

import io.github.javaquasar.hazelcast.toolkit.annotation.HzCompact;

@HzCompact(serializer = TestClientLimitEntryCacheCompactSerializer.class)
public class TestClientLimitEntryCache {

    private TestPeriod period;
    private Integer limitAmount = 0;
    private Integer counter = 0;
    private Integer previousCounterValue = 0;
    private Boolean limitReached = false;
    private Long startMillis;
    private Long thisSessionStartMillis;

    public TestPeriod getPeriod() {
        return period;
    }

    public void setPeriod(TestPeriod period) {
        this.period = period;
    }

    public Integer getLimitAmount() {
        return limitAmount;
    }

    public void setLimitAmount(Integer limitAmount) {
        this.limitAmount = limitAmount;
    }

    public Integer getCounter() {
        return counter;
    }

    public void setCounter(Integer counter) {
        this.counter = counter;
    }

    public Integer getPreviousCounterValue() {
        return previousCounterValue;
    }

    public void setPreviousCounterValue(Integer previousCounterValue) {
        this.previousCounterValue = previousCounterValue;
    }

    public Boolean getLimitReached() {
        return limitReached;
    }

    public void setLimitReached(Boolean limitReached) {
        this.limitReached = limitReached;
    }

    public Long getStartMillis() {
        return startMillis;
    }

    public void setStartMillis(Long startMillis) {
        this.startMillis = startMillis;
    }

    public Long getThisSessionStartMillis() {
        return thisSessionStartMillis;
    }

    public void setThisSessionStartMillis(Long thisSessionStartMillis) {
        this.thisSessionStartMillis = thisSessionStartMillis;
    }
}
