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
 * Lightweight HTTP endpoints for inspecting Hazelcast maps/caches and Near Cache stats.
 *
 * Designed for debugging and local profiling. You can secure or disable it in your application.
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

        if (near == null) {
            return Map.of("enabled", false);
        }

        return Map.of(
                "ownedEntryCount", near.getOwnedEntryCount(),
                "hits", near.getHits(),
                "misses", near.getMisses(),
                "ratio", near.getRatio(),
                "invalidations", near.getInvalidations(),
                "evictions", near.getEvictions(),
                "expirations", near.getExpirations(),
                "ownedEntryMemoryCost", near.getOwnedEntryMemoryCost()
        );
    }

    @GetMapping("/hz/jcache/near-stats/{cacheName}")
    public Map<String, Object> nearJCacheStats(@PathVariable String cacheName) {
        Cache<Object, Object> cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return Map.of("error", "Cache not found: " + cacheName);
        }

        ICache<Object, Object> icache = cache.unwrap(ICache.class);

        var local = icache.getLocalCacheStatistics();
        var near = local.getNearCacheStatistics();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cacheName", cacheName);

        Map<String, Object> l = new LinkedHashMap<>();
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

        if (near != null) {
            Map<String, Object> n = new LinkedHashMap<>();
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
            out.put("near", Map.of("enabled", false));
        }

        return out;
    }
}
