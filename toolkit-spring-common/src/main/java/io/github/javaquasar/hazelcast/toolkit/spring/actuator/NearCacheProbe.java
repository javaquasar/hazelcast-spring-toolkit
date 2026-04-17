package io.github.javaquasar.hazelcast.toolkit.spring.actuator;

import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties;
import org.hibernate.stat.Statistics;
import org.springframework.lang.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Core probe logic for the Hazelcast near-cache health-check Actuator endpoint.
 *
 * <p>This class is JPA-API-agnostic: it receives JPA operations via functional
 * parameters so that the caller can supply either the {@code javax.persistence}
 * (Spring Boot 2 / Hibernate 5) or the {@code jakarta.persistence}
 * (Spring Boot 3 / Hibernate 6) implementation without duplicating the probe
 * algorithm.
 *
 * <p>The probe runs three consecutive loads of the same entity, each in an
 * isolated persistence context so that the first-level cache never interferes:
 * <ol>
 *   <li>Initial load — populates the L2 / near-cache.</li>
 *   <li>Cached reload — must be served by the near-cache (hit verified).</li>
 *   <li>Post-eviction reload — must reach the database (invalidation verified).</li>
 * </ol>
 *
 * <p>Between steps 2 and 3 the entity is evicted cluster-wide via the JPA
 * {@code Cache.evict()} API, which propagates to all connected near-cache clients.
 *
 * @since 0.2.0
 */
public final class NearCacheProbe {

    /**
     * Loads an entity inside a short-lived, isolated persistence context.
     *
     * <p>Each call must open its own EntityManager (and commit/rollback its
     * transaction) so that only the second-level cache — not the first-level
     * session cache — can satisfy subsequent calls.
     */
    @FunctionalInterface
    public interface NewContextLoader {
        /**
         * @param entityClass the JPA entity type
         * @param entityId    the primary key value
         * @return the found entity, or {@code null} if not present
         */
        @Nullable Object load(Class<?> entityClass, Object entityId);
    }

    /**
     * Evicts an entity from the L2 cache cluster-wide.
     *
     * <p>Implementations must call the JPA {@code Cache.evict(Class, Object)}
     * method, which propagates the eviction to every near-cache client in the
     * Hazelcast cluster.
     */
    @FunctionalInterface
    public interface L2CacheEvictor {
        /**
         * @param entityClass the JPA entity type
         * @param entityId    the primary key value
         */
        void evict(Class<?> entityClass, Object entityId);
    }

    private final HzToolkitProperties properties;

    /**
     * Creates the probe.
     *
     * @param properties toolkit properties that supply the default entity class
     *                   and entity ID when no query-parameter overrides are provided
     */
    public NearCacheProbe(HzToolkitProperties properties) {
        this.properties = properties;
    }

    /**
     * Runs the near-cache probe.
     *
     * @param entity             fully-qualified JPA entity class name, or {@code null}
     *                           to use the configured default
     * @param id                 primary-key value as a string, or {@code null} to use
     *                           the configured default
     * @param statisticsSupplier provides Hibernate's {@link Statistics} for accurate
     *                           hit/miss detection; called once and the result is reused
     * @param loader             loads one entity in a fresh persistence context
     * @param evictor            evicts one entity from the L2 cache cluster-wide
     * @return a {@code LinkedHashMap} ready to be serialized to JSON by the Actuator
     */
    public Map<String, Object> check(
            @Nullable String entity,
            @Nullable String id,
            Supplier<Statistics> statisticsSupplier,
            NewContextLoader loader,
            L2CacheEvictor evictor) {

        HzToolkitProperties.Actuator.NearCacheCheck cfg =
                properties.getActuator().getNearCacheCheck();

        String entityClassName = firstNonBlank(entity, cfg.getEntityClass());
        String entityIdStr     = firstNonBlank(id,     cfg.getEntityId());

        if (entityClassName == null || entityClassName.isBlank()) {
            return errorResult("entity class is required — supply via query param or "
                    + "hazelcast.toolkit.actuator.near-cache-check.entity-class");
        }
        if (entityIdStr == null || entityIdStr.isBlank()) {
            return errorResult("entity id is required — supply via query param or "
                    + "hazelcast.toolkit.actuator.near-cache-check.entity-id");
        }

        Class<?> entityClass = resolveClass(entityClassName);
        if (entityClass == null) {
            return errorResult("Entity class not found on classpath: " + entityClassName);
        }

        Object entityId = resolveId(entityIdStr);

        try {
            return runProbe(entityClass, entityId, entityIdStr,
                    statisticsSupplier, loader, evictor);
        } catch (Exception ex) {
            return errorResult("Probe failed: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Core probe logic
    // ------------------------------------------------------------------

    private Map<String, Object> runProbe(
            Class<?> entityClass,
            Object entityId,
            String entityIdStr,
            Supplier<Statistics> statisticsSupplier,
            NewContextLoader loader,
            L2CacheEvictor evictor) {

        // Phase 0: verify the entity exists in the database
        boolean exists = loader.load(entityClass, entityId) != null;
        if (!exists) {
            return errorResult(entityClass.getSimpleName() + " with id=" + entityIdStr
                    + " not found in database — choose an existing entity");
        }

        Statistics stats = statisticsSupplier.get();
        boolean statsEnabled = stats.isStatisticsEnabled();

        // Phase 1: capture baseline after the initial load (entity now in L2 / near-cache)
        long hitsBaseline   = statsEnabled ? stats.getSecondLevelCacheHitCount()  : 0;
        long missesBaseline = statsEnabled ? stats.getSecondLevelCacheMissCount() : 0;

        // Phase 2: reload from a fresh context — must be served by L2 / near-cache
        long t1 = System.nanoTime();
        loader.load(entityClass, entityId);
        long cachedLoadMs = nanosToMillis(System.nanoTime() - t1);

        long hitsAfterCached = statsEnabled ? stats.getSecondLevelCacheHitCount() : 0;
        boolean cacheHit = statsEnabled
                ? hitsAfterCached > hitsBaseline
                : cachedLoadMs < 5;   // < 5 ms heuristic when statistics are off

        // Phase 3: evict the entity from the L2 cache — propagates cluster-wide,
        // including near-cache invalidation on this and all other connected clients
        evictor.evict(entityClass, entityId);

        // Phase 4: reload after eviction — must go to database (L2 and near-cache are cold)
        long hitsBeforeEvict   = statsEnabled ? stats.getSecondLevelCacheHitCount()  : 0;
        long missesBeforeEvict = statsEnabled ? stats.getSecondLevelCacheMissCount() : 0;
        long t2 = System.nanoTime();
        loader.load(entityClass, entityId);
        long postEvictLoadMs = nanosToMillis(System.nanoTime() - t2);

        long hitsAfterEvict   = statsEnabled ? stats.getSecondLevelCacheHitCount()  : 0;
        long missesAfterEvict = statsEnabled ? stats.getSecondLevelCacheMissCount() : 0;
        // No new hits and at least one miss → went to the database
        boolean invalidationVerified = statsEnabled
                ? (hitsAfterEvict == hitsBeforeEvict && missesAfterEvict > missesBeforeEvict)
                : (postEvictLoadMs > cachedLoadMs * 3);

        // Build result map
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("entity", entityClass.getName());
        result.put("id", entityIdStr);

        Map<String, Object> nearCacheSection = new LinkedHashMap<>();
        nearCacheSection.put("hitVerified", cacheHit);
        nearCacheSection.put("invalidationVerified", invalidationVerified);
        result.put("nearCache", nearCacheSection);

        Map<String, Object> timings = new LinkedHashMap<>();
        timings.put("cachedLoadMs", cachedLoadMs);
        timings.put("postEvictionLoadMs", postEvictLoadMs);
        result.put("timings", timings);

        if (statsEnabled) {
            Map<String, Object> hStats = new LinkedHashMap<>();
            hStats.put("l2HitsDeltaOnCachedLoad",    hitsAfterCached   - hitsBaseline);
            hStats.put("l2MissesDeltaAfterEviction",  missesAfterEvict  - missesBeforeEvict);
            hStats.put("l2HitsDeltaAfterEviction",    hitsAfterEvict    - hitsBeforeEvict);
            result.put("hibernateStats", hStats);
        } else {
            result.put("hint", "Enable hazelcast.toolkit.hibernate.l2.use-statistics=true "
                    + "for precise hit/miss detection; timing heuristics are used as fallback");
        }

        return result;
    }

    // ------------------------------------------------------------------
    // Static helpers
    // ------------------------------------------------------------------

    /**
     * Resolves an entity ID string to a typed value.
     *
     * <p>Tries {@code Long} first (most common for auto-increment primary keys),
     * then {@code Integer}, then returns the raw string for string-keyed entities.
     */
    public static Object resolveId(String idStr) {
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Integer.parseInt(idStr);
        } catch (NumberFormatException ignored) {
        }
        return idStr;
    }

    /**
     * Resolves a fully-qualified class name to a {@link Class}, or returns
     * {@code null} if the class is not found on the current thread's context
     * class loader.
     */
    @Nullable
    public static Class<?> resolveClass(String className) {
        try {
            return Class.forName(className, true,
                    Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    /**
     * Builds a standard error response map.
     */
    public static Map<String, Object> errorResult(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ERROR");
        result.put("error", message);
        return result;
    }

    private static String firstNonBlank(@Nullable String a, @Nullable String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    private static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }
}
