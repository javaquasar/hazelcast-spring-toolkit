package io.github.javaquasar.hazelcast.toolkit.springboot3.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hazelcast.toolkit")
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
        /** Base package to scan for @HzCompact types */
        private String basePackage = "";

        public String getBasePackage() {
            return basePackage;
        }

        public void setBasePackage(String basePackage) {
            this.basePackage = basePackage;
        }
    }

    public static class Metrics {
        /** Enables HTTP endpoints in HzToolkitMetricsController */
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
