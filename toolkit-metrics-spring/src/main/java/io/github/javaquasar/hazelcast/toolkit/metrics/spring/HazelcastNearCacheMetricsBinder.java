package io.github.javaquasar.hazelcast.toolkit.metrics.spring;

import com.hazelcast.cache.ICache;
import com.hazelcast.core.DistributedObject;
import com.hazelcast.core.DistributedObjectEvent;
import com.hazelcast.core.DistributedObjectListener;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.nearcache.NearCacheStats;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.DisposableBean;

import javax.cache.Cache;
import javax.cache.CacheManager;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.ToLongFunction;

/**
 * Micrometer binder exposing Hazelcast near-cache metrics for both {@link IMap}
 * and JCache / {@link ICache} data structures.
 *
 * <p>The binder registers meters for already existing distributed objects at startup
 * and listens for new distributed objects so runtime-created maps and caches are
 * instrumented automatically.
 *
 * <p>The exported metrics are intentionally primitive counters and gauges rather than
 * precomputed ratios:
 * <ul>
 *   <li>{@code hazelcast.toolkit.near.cache.enabled}</li>
 *   <li>{@code hazelcast.toolkit.near.cache.hits}</li>
 *   <li>{@code hazelcast.toolkit.near.cache.misses}</li>
 *   <li>{@code hazelcast.toolkit.near.cache.invalidations}</li>
 *   <li>{@code hazelcast.toolkit.near.cache.evictions}</li>
 *   <li>{@code hazelcast.toolkit.near.cache.expirations}</li>
 *   <li>{@code hazelcast.toolkit.near.cache.owned.entries}</li>
 *   <li>{@code hazelcast.toolkit.near.cache.owned.entry.memory.bytes}</li>
 * </ul>
 *
 * <p>Tags:
 * <ul>
 *   <li>{@code cache}</li>
 *   <li>{@code kind} = {@code imap} or {@code jcache}</li>
 * </ul>
 */
public class HazelcastNearCacheMetricsBinder implements MeterBinder, DisposableBean {

    private static final String METRIC_PREFIX = "hazelcast.toolkit.near.cache";

    private final CacheManager cacheManager;
    private final HazelcastInstance hazelcastInstance;
    private final Set<MeterRegistry> boundRegistries = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<String, NearCacheMetricTarget> mapTargets = new ConcurrentHashMap<>();
    private final Map<String, NearCacheMetricTarget> cacheTargets = new ConcurrentHashMap<>();
    private final AtomicBoolean listenerRegistered = new AtomicBoolean(false);

    private volatile UUID distributedObjectListenerId;

    public HazelcastNearCacheMetricsBinder(CacheManager cacheManager,
                                           HazelcastInstance hazelcastInstance) {
        this.cacheManager = cacheManager;
        this.hazelcastInstance = hazelcastInstance;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        synchronized (boundRegistries) {
            if (!boundRegistries.add(registry)) {
                return;
            }
        }
        registerDistributedObjectListener();
        registerExistingDistributedObjects(registry);
        registerExistingCaches(registry);
    }

    @Override
    public void destroy() {
        if (distributedObjectListenerId != null) {
            hazelcastInstance.removeDistributedObjectListener(distributedObjectListenerId);
        }
    }

    private void registerExistingDistributedObjects(MeterRegistry registry) {
        for (DistributedObject object : hazelcastInstance.getDistributedObjects()) {
            registerDistributedObject(registry, object);
        }
    }

    private void registerExistingCaches(MeterRegistry registry) {
        for (String cacheName : cacheManager.getCacheNames()) {
            registerJCacheMetrics(registry, cacheName);
        }
    }

    private void registerDistributedObjectListener() {
        if (!listenerRegistered.compareAndSet(false, true)) {
            return;
        }
        distributedObjectListenerId = hazelcastInstance.addDistributedObjectListener(new DistributedObjectListener() {
            @Override
            public void distributedObjectCreated(DistributedObjectEvent event) {
                for (MeterRegistry registry : boundRegistrySnapshot()) {
                    registerDistributedObject(registry, event.getDistributedObject());
                }
            }

            @Override
            public void distributedObjectDestroyed(DistributedObjectEvent event) {
                // Meters remain registered and safely fall back to zero once the
                // underlying distributed object disappears.
            }
        });
    }

    private MeterRegistry[] boundRegistrySnapshot() {
        synchronized (boundRegistries) {
            return boundRegistries.toArray(MeterRegistry[]::new);
        }
    }

    private void registerDistributedObject(MeterRegistry registry, DistributedObject object) {
        if (object instanceof IMap<?, ?>) {
            registerIMapMetrics(registry, object.getName());
            return;
        }
        if (object instanceof ICache<?, ?> || isCacheDistributedObject(object)) {
            registerJCacheMetrics(registry, object.getName());
        }
    }

    private boolean isCacheDistributedObject(DistributedObject object) {
        String serviceName = object.getServiceName();
        return serviceName != null && serviceName.toLowerCase(Locale.ROOT).contains("cache");
    }

    private void registerIMapMetrics(MeterRegistry registry, String mapName) {
        Tags tags = Tags.of("cache", mapName, "kind", "imap");
        NearCacheMetricTarget target = mapTargets.computeIfAbsent(
                mapName,
                ignored -> new NearCacheMetricTarget(
                        () -> findMapNearCacheStats(mapName) != null,
                        extractor -> readMapNearCacheCounter(mapName, extractor),
                        extractor -> readMapNearCacheGauge(mapName, extractor)
                )
        );
        registerNearCacheMeters(registry, tags, target);
    }

    private void registerJCacheMetrics(MeterRegistry registry, String cacheName) {
        Tags tags = Tags.of("cache", cacheName, "kind", "jcache");
        NearCacheMetricTarget target = cacheTargets.computeIfAbsent(
                cacheName,
                ignored -> new NearCacheMetricTarget(
                        () -> findJCacheNearCacheStats(cacheName) != null,
                        extractor -> readJCacheNearCacheCounter(cacheName, extractor),
                        extractor -> readJCacheNearCacheGauge(cacheName, extractor)
                )
        );
        registerNearCacheMeters(registry, tags, target);
    }

    private void registerNearCacheMeters(
            MeterRegistry registry,
            Tags tags,
            NearCacheMetricTarget target) {

        Gauge.builder(METRIC_PREFIX + ".enabled", target, meterTarget -> meterTarget.enabledSupplier.isEnabled() ? 1.0d : 0.0d)
                .tags(tags)
                .register(registry);

        FunctionCounter.builder(METRIC_PREFIX + ".hits", target, meterTarget -> meterTarget.counterReader.read(NearCacheStats::getHits))
                .tags(tags)
                .register(registry);
        FunctionCounter.builder(METRIC_PREFIX + ".misses", target, meterTarget -> meterTarget.counterReader.read(NearCacheStats::getMisses))
                .tags(tags)
                .register(registry);
        FunctionCounter.builder(METRIC_PREFIX + ".invalidations", target, meterTarget -> meterTarget.counterReader.read(NearCacheStats::getInvalidations))
                .tags(tags)
                .register(registry);
        FunctionCounter.builder(METRIC_PREFIX + ".evictions", target, meterTarget -> meterTarget.counterReader.read(NearCacheStats::getEvictions))
                .tags(tags)
                .register(registry);
        FunctionCounter.builder(METRIC_PREFIX + ".expirations", target, meterTarget -> meterTarget.counterReader.read(NearCacheStats::getExpirations))
                .tags(tags)
                .register(registry);

        Gauge.builder(METRIC_PREFIX + ".owned.entries", target, meterTarget -> meterTarget.gaugeReader.read(NearCacheStats::getOwnedEntryCount))
                .tags(tags)
                .register(registry);
        Gauge.builder(METRIC_PREFIX + ".owned.entry.memory.bytes", target, meterTarget -> meterTarget.gaugeReader.read(NearCacheStats::getOwnedEntryMemoryCost))
                .tags(tags)
                .register(registry);
    }

    private double readMapNearCacheCounter(String mapName, ToLongFunction<NearCacheStats> extractor) {
        NearCacheStats stats = findMapNearCacheStats(mapName);
        return stats == null ? 0.0d : extractor.applyAsLong(stats);
    }

    private double readMapNearCacheGauge(String mapName, ToLongFunction<NearCacheStats> extractor) {
        return readMapNearCacheCounter(mapName, extractor);
    }

    private double readJCacheNearCacheCounter(String cacheName, ToLongFunction<NearCacheStats> extractor) {
        NearCacheStats stats = findJCacheNearCacheStats(cacheName);
        return stats == null ? 0.0d : extractor.applyAsLong(stats);
    }

    private double readJCacheNearCacheGauge(String cacheName, ToLongFunction<NearCacheStats> extractor) {
        return readJCacheNearCacheCounter(cacheName, extractor);
    }

    private NearCacheStats findMapNearCacheStats(String mapName) {
        for (DistributedObject object : hazelcastInstance.getDistributedObjects()) {
            if (object instanceof IMap<?, ?> map && object.getName().equals(mapName)) {
                return map.getLocalMapStats().getNearCacheStats();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private NearCacheStats findJCacheNearCacheStats(String cacheName) {
        Cache<Object, Object> cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return null;
        }
        try {
            ICache<Object, Object> hazelcastCache = cache.unwrap(ICache.class);
            if (hazelcastCache.getLocalCacheStatistics() == null) {
                return null;
            }
            return hazelcastCache.getLocalCacheStatistics().getNearCacheStatistics();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @FunctionalInterface
    private interface NearCacheEnabledSupplier {
        boolean isEnabled();
    }

    @FunctionalInterface
    private interface NearCacheCounterReader {
        double read(ToLongFunction<NearCacheStats> extractor);
    }

    @FunctionalInterface
    private interface NearCacheGaugeReader {
        double read(ToLongFunction<NearCacheStats> extractor);
    }

    private static final class NearCacheMetricTarget {
        private final NearCacheEnabledSupplier enabledSupplier;
        private final NearCacheCounterReader counterReader;
        private final NearCacheGaugeReader gaugeReader;

        private NearCacheMetricTarget(
                NearCacheEnabledSupplier enabledSupplier,
                NearCacheCounterReader counterReader,
                NearCacheGaugeReader gaugeReader) {
            this.enabledSupplier = enabledSupplier;
            this.counterReader = counterReader;
            this.gaugeReader = gaugeReader;
        }
    }
}
