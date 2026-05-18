package io.github.javaquasar.hazelcast.toolkit.metrics.spring;

import com.hazelcast.cache.ICache;
import com.hazelcast.core.DistributedObject;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.nearcache.NearCacheStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.cache.Cache;
import javax.cache.CacheManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight HTTP diagnostics endpoints for inspecting Hazelcast maps, caches,
 * and near-cache state.
 *
 * <p>This controller is intentionally positioned as a diagnostic / debugging tool,
 * not as the primary production metrics API. For production monitoring, prefer the
 * Micrometer binders exposed by the toolkit.
 */
@RestController
@RequestMapping(value = "/hz-toolkit", produces = MediaType.APPLICATION_JSON_VALUE)
public class HzToolkitMetricsController {

    private static final Logger log = LoggerFactory.getLogger(HzToolkitMetricsController.class);

    private final CacheManager cacheManager;
    private final HazelcastInstance hazelcastInstance;

    public HzToolkitMetricsController(CacheManager cacheManager, HazelcastInstance hazelcastInstance) {
        this.cacheManager = cacheManager;
        this.hazelcastInstance = hazelcastInstance;
    }

    @GetMapping("/hz/objects")
    public List<Map<String, String>> objects() {
        return hazelcastInstance.getDistributedObjects().stream()
                .map(o -> Map.of(
                        "serviceName", o.getServiceName(),
                        "name", o.getName()
                ))
                .toList();
    }

    @GetMapping("/hz/maps")
    public List<String> maps() {
        return hazelcastInstance.getDistributedObjects().stream()
                .filter(o -> o instanceof IMap)
                .map(DistributedObject::getName)
                .sorted()
                .toList();
    }

    @GetMapping("/hz/map/near-stats/{mapName}")
    public Map<String, Object> nearMapStats(@PathVariable String mapName) {
        IMap<Object, Object> map = hazelcastInstance.getMap(mapName);
        NearCacheStats near = map.getLocalMapStats().getNearCacheStats();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "OK");
        out.put("name", mapName);
        out.put("mapName", mapName);

        if (near == null) {
            out.put("enabled", false);
            out.put("near", Map.of("enabled", false, "reason", "Near Cache is not enabled"));
            return out;
        }

        Map<String, Object> n = new LinkedHashMap<>();
        n.put("enabled", true);
        n.put("ownedEntryCount", near.getOwnedEntryCount());
        n.put("hits", near.getHits());
        n.put("misses", near.getMisses());
        n.put("ratio", near.getRatio());
        n.put("invalidations", near.getInvalidations());
        n.put("evictions", near.getEvictions());
        n.put("expirations", near.getExpirations());
        n.put("ownedEntryMemoryCost", near.getOwnedEntryMemoryCost());

        out.putAll(n);
        out.put("near", n);
        return out;
    }

    @GetMapping("/hz/jcache/near-stats/{cacheName}")
    public Map<String, Object> nearJCacheStats(@PathVariable String cacheName) {
        Cache<Object, Object> cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", "ERROR");
            out.put("name", cacheName);
            out.put("cacheName", cacheName);
            out.put("error", "Cache not found: " + cacheName);
            out.put("local", Map.of("available", false));
            out.put("near", Map.of("enabled", false, "reason", "Cache not found"));
            return out;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "OK");
        out.put("name", cacheName);
        out.put("cacheName", cacheName);

        ICache<Object, Object> icache;
        try {
            icache = cache.unwrap(ICache.class);
        } catch (RuntimeException ex) {
            out.put("status", "ERROR");
            out.put("error", "Cache cannot be unwrapped to Hazelcast ICache: " + cacheName);
            out.put("local", Map.of("available", false, "reason", reason(ex)));
            out.put("near", Map.of("enabled", false, "reason", "Hazelcast ICache is not available"));
            return out;
        }

        var local = icache.getLocalCacheStatistics();
        if (local == null) {
            out.put("local", Map.of("available", false, "reason", "Local cache statistics are not available"));
            out.put("near", Map.of("enabled", false, "reason", "Local cache statistics are not available"));
            return out;
        }

        Map<String, Object> l = new LinkedHashMap<>();
        l.put("available", true);
        l.put("creationTime", local.getCreationTime());
        l.put("lastAccessTime", local.getLastAccessTime());
        l.put("lastUpdateTime", local.getLastUpdateTime());

        // Some stats are not supported on the client side.
        l.put("cacheGets", local.getCacheGets());
        l.put("cachePuts", local.getCachePuts());
        l.put("cacheRemovals", local.getCacheRemovals());

        l.put("cacheHits", local.getCacheHits());
        l.put("cacheMisses", local.getCacheMisses());
        l.put("cacheHitPercentage", local.getCacheHitPercentage());
        l.put("cacheMissPercentage", local.getCacheMissPercentage());

        l.put("averageGetTime", local.getAverageGetTime());
        l.put("averagePutTime", local.getAveragePutTime());
        l.put("averageRemoveTime", local.getAverageRemoveTime());

        out.put("local", l);

        NearCacheStats near = null;
        RuntimeException nearStatsError = null;
        try {
            near = local.getNearCacheStatistics();
        } catch (UnsupportedOperationException ex) {
            nearStatsError = ex;
        } catch (RuntimeException ex) {
            nearStatsError = ex;
        }

        if (near != null) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("enabled", true);
            n.put("ownedEntryCount", near.getOwnedEntryCount());
            n.put("hits", near.getHits());
            n.put("misses", near.getMisses());
            n.put("ratio", near.getRatio());
            n.put("invalidations", near.getInvalidations());

            n.put("evictions", near.getEvictions());
            n.put("expirations", near.getExpirations());
            n.put("ownedEntryMemoryCost", near.getOwnedEntryMemoryCost());

            out.put("near", n);
        } else {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("enabled", false);
            if (nearStatsError != null) {
                n.put("reason", reason(nearStatsError));
            }
            out.put("near", n);
        }

        return out;
    }

    private static String reason(RuntimeException ex) {
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
