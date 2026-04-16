package io.github.javaquasar.hazelcast.toolkit.hazelcast.config;

public class HzToolkitProperties {

    private Compact compact = new Compact();
    private Metrics metrics = new Metrics();
    private Client client = new Client();
    private Hibernate hibernate = new Hibernate();

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

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public Hibernate getHibernate() {
        return hibernate;
    }

    public void setHibernate(Hibernate hibernate) {
        this.hibernate = hibernate;
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

    public static class Client {
        private String baseName = "";

        public String getBaseName() {
            return baseName;
        }

        public void setBaseName(String baseName) {
            this.baseName = baseName;
        }
    }

    /**
     * Hibernate second-level cache settings managed by the toolkit.
     * <p>
     * Activate with {@code hazelcast.toolkit.hibernate.l2.enabled=true}.
     */
    public static class Hibernate {
        private L2 l2 = new L2();

        public L2 getL2() {
            return l2;
        }

        public void setL2(L2 l2) {
            this.l2 = l2;
        }

        public static class L2 {
            /**
             * When {@code true} the toolkit registers a {@code HibernatePropertiesCustomizer}
             * that wires Hibernate's second-level cache to the toolkit-managed JCache
             * {@code CacheManager}.
             */
            private boolean enabled = false;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }
    }
}
