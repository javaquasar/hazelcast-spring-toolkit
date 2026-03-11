package io.github.javaquasar.hazelcast.toolkit.hazelcast.config;

public class HzToolkitProperties {

    private Compact compact = new Compact();
    private Metrics metrics = new Metrics();

    public Compact getCompact() {
        return compact;
    }

    public void setCompact(Compact compact) {
        this.compact = compact;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public void setMetrics(Metrics metrics) {
        this.metrics = metrics;
    }

    public static class Compact {
        private String basePackage = "";

        public String getBasePackage() {
            return basePackage;
        }

        public void setBasePackage(String basePackage) {
            this.basePackage = basePackage;
        }
    }

    public static class Metrics {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
