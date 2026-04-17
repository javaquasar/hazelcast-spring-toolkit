package io.github.javaquasar.hazelcast.toolkit.hazelcast.config;

/**
 * Configuration properties bound under the {@code hazelcast.toolkit} prefix.
 *
 * <p>Controls toolkit-specific behaviour such as compact serialization scanning,
 * client naming, optional metrics, and Hibernate second-level cache integration.
 *
 * <p>Example {@code application.yml}:
 * <pre>{@code
 * hazelcast:
 *   toolkit:
 *     compact:
 *       base-package: com.example.model
 *     client:
 *       base-name: my-service
 *     metrics:
 *       enabled: true
 *     hibernate:
 *       l2:
 *         enabled: true
 *         extended-config: true
 * }</pre>
 *
 * @since 0.1.0
 */
public class HzToolkitProperties {

    private Compact compact = new Compact();
    private Metrics metrics = new Metrics();
    private Client client = new Client();
    private Hibernate hibernate = new Hibernate();
    private Actuator actuator = new Actuator();

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

    public Actuator getActuator() {
        return actuator;
    }

    public void setActuator(Actuator actuator) {
        this.actuator = actuator;
    }

    /**
     * Compact serialization scanning settings.
     */
    public static class Compact {
        private String basePackage = "";

        public String getBasePackage() {
            return basePackage;
        }

        public void setBasePackage(String basePackage) {
            this.basePackage = basePackage;
        }
    }

    /**
     * Metrics REST endpoint settings.
     */
    public static class Metrics {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Client naming settings.
     * Takes precedence over {@link HazelcastClientProperties#getInstanceName()}.
     */
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
     *
     * <p>Activate with {@code hazelcast.toolkit.hibernate.l2.enabled=true}.
     */
    public static class Hibernate {
        private L2 l2 = new L2();

        public L2 getL2() {
            return l2;
        }

        public void setL2(L2 l2) {
            this.l2 = l2;
        }

        /**
         * Second-level cache configuration properties ({@code hazelcast.toolkit.hibernate.l2.*}).
         *
         * <p><b>Design philosophy:</b> by default the toolkit is non-intrusive.
         * It enables Hibernate's L2 cache mechanism but deliberately avoids dictating
         * <em>how</em> that cache is wired.  Applications that already manage their own
         * Hibernate cache configuration will not be affected.  Opt into the extended
         * property set only when you want the toolkit to handle the full wiring.
         *
         * <h2>Minimal mode — {@code extended-config=false} (default)</h2>
         * <p>The toolkit sets only what is strictly necessary:
         * <ul>
         *   <li><b>JCACHE mode</b> — sets {@code hibernate.cache.use_second_level_cache=true} only.
         *       All JCache provider and factory-class wiring is left to the application.</li>
         *   <li><b>HAZELCAST_LOCAL / HAZELCAST modes</b> — additionally sets
         *       {@code hibernate.cache.region.factory_class} and {@code hazelcast.instance.name}.
         *       Both are skipped (with a WARN) if {@code region.factory_class} is already present.</li>
         * </ul>
         * All other Hibernate cache properties ({@code use_query_cache}, {@code generate_statistics},
         * etc.) are untouched — configure them directly via {@code spring.jpa.properties.*}
         * if needed.
         *
         * <h2>Extended configuration mode — {@code extended-config=true}</h2>
         * <p>The toolkit applies the complete property set using {@code putIfAbsent}, so
         * any value already present in {@code spring.jpa.properties.*} always takes precedence.
         * In JCACHE mode this additionally wires {@code region.factory_class}, the Hazelcast
         * JCache provider, and the {@code CacheManager} instance binding.
         * {@code use-query-cache} and {@code use-statistics} are also applied (both default
         * to {@code false}).
         *
         * <p><b>Migration note:</b> if you relied on {@code enabled=true} alone to configure the
         * full JCACHE path in a previous version of this library, add
         * {@code extended-config=true} to restore that behaviour.
         */
        public static class L2 {

            /**
             * Master switch. Set to {@code true} to activate Hibernate second-level cache
             * auto-configuration.
             */
            private boolean enabled = false;

            /**
             * Selects the Hibernate {@code RegionFactory} implementation.
             * Defaults to {@link RegionFactoryType#JCACHE} — the safest choice when a
             * toolkit-managed {@code CacheManager} is present.
             */
            private RegionFactoryType regionFactory = RegionFactoryType.JCACHE;

            /**
             * When {@code true}, applies the full set of Hibernate cache properties
             * ({@code region.factory_class}, {@code use_query_cache}, {@code generate_statistics},
             * and — for JCACHE mode — the JCache provider and {@code CacheManager} binding).
             *
             * <p>All properties are written with {@code putIfAbsent}: values already present
             * in {@code spring.jpa.properties.*} are never overwritten.
             *
             * <p>Defaults to {@code false} — the toolkit is non-intrusive by default.
             */
            private boolean extendedConfig = false;

            /**
             * Whether Hibernate's query result cache is enabled.
             * Applied only when {@link #isExtendedConfig()} is {@code true}.
             *
             * <p>Defaults to {@code false}. Enable explicitly when query caching is needed.
             */
            private boolean useQueryCache = false;

            /**
             * Whether Hibernate cache statistics are collected.
             * Applied only when {@link #isExtendedConfig()} is {@code true}.
             *
             * <p>Defaults to {@code false} to avoid performance overhead in production.
             */
            private boolean useStatistics = false;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public RegionFactoryType getRegionFactory() {
                return regionFactory;
            }

            public void setRegionFactory(RegionFactoryType regionFactory) {
                this.regionFactory = regionFactory;
            }

            public boolean isExtendedConfig() {
                return extendedConfig;
            }

            public void setExtendedConfig(boolean extendedConfig) {
                this.extendedConfig = extendedConfig;
            }

            public boolean isUseQueryCache() {
                return useQueryCache;
            }

            public void setUseQueryCache(boolean useQueryCache) {
                this.useQueryCache = useQueryCache;
            }

            public boolean isUseStatistics() {
                return useStatistics;
            }

            public void setUseStatistics(boolean useStatistics) {
                this.useStatistics = useStatistics;
            }

            /**
             * Selects the Hibernate second-level cache {@code RegionFactory} implementation.
             *
             * <p><b>Which one should I use?</b>
             * <ul>
             *   <li>Start with {@link #JCACHE} — it is the safest default and requires no
             *       extra dependency.</li>
             *   <li>If you need a pure Hazelcast-native cache path (no JCache layer),
             *       use {@link #HAZELCAST_LOCAL} — it is the recommended choice for most
             *       Hazelcast client applications.</li>
             *   <li>Use {@link #HAZELCAST} only when you have a specific requirement for
             *       strong cluster-wide consistency with no near-cache.</li>
             * </ul>
             */
            public enum RegionFactoryType {

                /**
                 * <b>Default — recommended for most applications.</b>
                 *
                 * <p>Uses the toolkit-managed {@code javax.cache.CacheManager} via Hazelcast's
                 * JCache provider.  No extra dependency required beyond the toolkit module itself.
                 *
                 * <p>With {@code extended-config=false} (default): only
                 * {@code hibernate.cache.use_second_level_cache=true} is set — the application
                 * retains full control over JCache wiring.
                 * With {@code extended-config=true}: the toolkit additionally sets
                 * {@code region.factory_class}, the Hazelcast JCache provider, the
                 * {@code CacheManager} binding, {@code use_query_cache}, and
                 * {@code generate_statistics}.
                 */
                JCACHE,

                /**
                 * <b>Recommended native mode for Hazelcast client applications.</b>
                 *
                 * <p>Uses {@code com.hazelcast.hibernate.HazelcastLocalCacheRegionFactory},
                 * which maintains a local near-cache on the client JVM for fast reads while
                 * propagating writes and invalidations through the cluster.  This avoids the
                 * JCache abstraction layer and gives Hibernate direct access to Hazelcast maps.
                 *
                 * <p>Requires {@code com.hazelcast:hazelcast-hibernate} on the classpath.
                 * The toolkit validates this at startup and throws {@link IllegalStateException}
                 * with dependency instructions if the class is not found.
                 */
                HAZELCAST_LOCAL,

                /**
                 * <b>Advanced — full distributed mode without a local near-cache.</b>
                 *
                 * <p>Uses {@code com.hazelcast.hibernate.HazelcastCacheRegionFactory}.
                 * Every cache read and write goes directly to the Hazelcast cluster.
                 * Choose this mode only when strong consistency across all cluster members
                 * is required and the additional network round-trip per cache access is
                 * acceptable.
                 *
                 * <p>Requires {@code com.hazelcast:hazelcast-hibernate} on the classpath.
                 */
                HAZELCAST
            }
        }
    }

    /**
     * Actuator endpoint settings ({@code hazelcast.toolkit.actuator.*}).
     */
    public static class Actuator {

        private NearCacheCheck nearCacheCheck = new NearCacheCheck();

        public NearCacheCheck getNearCacheCheck() {
            return nearCacheCheck;
        }

        public void setNearCacheCheck(NearCacheCheck nearCacheCheck) {
            this.nearCacheCheck = nearCacheCheck;
        }

        /**
         * Near-cache health-check endpoint settings
         * ({@code hazelcast.toolkit.actuator.near-cache-check.*}).
         *
         * <p>When enabled, registers a Spring Boot Actuator endpoint at
         * {@code /actuator/hazelcast-near-cache} that probes whether the Hazelcast
         * near-cache is hitting correctly and whether L2-cache evictions propagate as
         * near-cache invalidations.
         *
         * <p>Requires {@code spring-boot-actuator} and {@code jakarta.persistence}
         * on the classpath. Both JCache and native ({@code HAZELCAST_LOCAL}) region-factory
         * modes are supported.
         *
         * <p>Example {@code application.yml}:
         * <pre>{@code
         * hazelcast:
         *   toolkit:
         *     actuator:
         *       near-cache-check:
         *         enabled: true
         *         entity-class: com.mycompany.entity.User
         *         entity-id: "42"
         * }</pre>
         *
         * <p><b>Production safety:</b> the check is read-only except for a single
         * targeted {@code Cache.evict()} call on the probe entity. Secure the endpoint
         * via Spring Security and avoid calling it in tight loops.
         */
        public static class NearCacheCheck {

            /**
             * Master switch. Set to {@code true} to register the
             * {@code /actuator/hazelcast-near-cache} endpoint.
             */
            private boolean enabled = false;

            /**
             * Fully-qualified class name of a cacheable JPA entity used as the probe
             * (e.g. {@code com.mycompany.entity.User}).
             *
             * <p>The entity must be annotated with Hibernate {@code @Cache} and the row
             * identified by {@link #entityId} must exist in the database.
             * Can be overridden per-request via the {@code entity} query parameter.
             */
            private String entityClass;

            /**
             * Primary-key value of the probe entity, expressed as a {@code String}.
             * The endpoint converts it to {@code Long} automatically; for other ID types
             * override via the {@code id} query parameter.
             *
             * <p>Can be overridden per-request via the {@code id} query parameter.
             */
            private String entityId;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getEntityClass() {
                return entityClass;
            }

            public void setEntityClass(String entityClass) {
                this.entityClass = entityClass;
            }

            public String getEntityId() {
                return entityId;
            }

            public void setEntityId(String entityId) {
                this.entityId = entityId;
            }
        }
    }
}
