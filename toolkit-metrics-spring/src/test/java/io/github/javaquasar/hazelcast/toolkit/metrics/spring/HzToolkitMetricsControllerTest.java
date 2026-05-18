package io.github.javaquasar.hazelcast.toolkit.metrics.spring;

import com.hazelcast.cache.ICache;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.map.LocalMapStats;
import org.junit.jupiter.api.Test;

import javax.cache.Cache;
import javax.cache.CacheManager;
import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HzToolkitMetricsControllerTest {

    @Test
    void mapNearStatsReturnsReasonWhenNearCacheIsDisabled() {
        HzToolkitMetricsController controller = new HzToolkitMetricsController(
                cacheManager(null),
                hazelcastInstanceWithMap(mapWithNoNearCacheStats())
        );

        Map<String, Object> result = controller.nearMapStats("plain-map");

        assertEquals("OK", result.get("status"));
        assertEquals("plain-map", result.get("name"));
        assertEquals("plain-map", result.get("mapName"));
        assertEquals(false, result.get("enabled"));

        @SuppressWarnings("unchecked")
        Map<String, Object> near = (Map<String, Object>) result.get("near");
        assertEquals(false, near.get("enabled"));
        assertEquals("Near Cache is not enabled", near.get("reason"));
    }

    @Test
    void jcacheNearStatsReturnsErrorContractWhenCacheIsMissing() {
        HzToolkitMetricsController controller = new HzToolkitMetricsController(
                cacheManager(null),
                proxy(HazelcastInstance.class)
        );

        Map<String, Object> result = controller.nearJCacheStats("missing-cache");

        assertEquals("ERROR", result.get("status"));
        assertEquals("missing-cache", result.get("name"));
        assertEquals("missing-cache", result.get("cacheName"));
        assertTrue(result.get("error").toString().contains("Cache not found"));

        @SuppressWarnings("unchecked")
        Map<String, Object> local = (Map<String, Object>) result.get("local");
        assertEquals(false, local.get("available"));

        @SuppressWarnings("unchecked")
        Map<String, Object> near = (Map<String, Object>) result.get("near");
        assertEquals(false, near.get("enabled"));
    }

    @Test
    void jcacheNearStatsReturnsErrorContractWhenUnwrapFails() {
        Cache<Object, Object> cache = cacheThrowingOnUnwrap(new IllegalArgumentException("not a Hazelcast cache"));
        HzToolkitMetricsController controller = new HzToolkitMetricsController(
                cacheManager(cache),
                proxy(HazelcastInstance.class)
        );

        Map<String, Object> result = controller.nearJCacheStats("plain-cache");

        assertEquals("ERROR", result.get("status"));
        assertEquals("plain-cache", result.get("name"));
        assertEquals("plain-cache", result.get("cacheName"));
        assertTrue(result.get("error").toString().contains("cannot be unwrapped"));

        @SuppressWarnings("unchecked")
        Map<String, Object> local = (Map<String, Object>) result.get("local");
        assertEquals(false, local.get("available"));
        assertTrue(local.get("reason").toString().contains("not a Hazelcast cache"));

        @SuppressWarnings("unchecked")
        Map<String, Object> near = (Map<String, Object>) result.get("near");
        assertEquals(false, near.get("enabled"));
        assertEquals("Hazelcast ICache is not available", near.get("reason"));
    }

    @Test
    void jcacheNearStatsReturnsDisabledSectionsWhenLocalStatsAreMissing() {
        Cache<Object, Object> cache = cacheReturningHazelcastCacheWithNoLocalStats();
        HzToolkitMetricsController controller = new HzToolkitMetricsController(
                cacheManager(cache),
                proxy(HazelcastInstance.class)
        );

        Map<String, Object> result = controller.nearJCacheStats("hazelcast-cache");

        assertEquals("OK", result.get("status"));
        assertEquals("hazelcast-cache", result.get("name"));
        assertEquals("hazelcast-cache", result.get("cacheName"));

        @SuppressWarnings("unchecked")
        Map<String, Object> local = (Map<String, Object>) result.get("local");
        assertEquals(false, local.get("available"));
        assertEquals("Local cache statistics are not available", local.get("reason"));

        @SuppressWarnings("unchecked")
        Map<String, Object> near = (Map<String, Object>) result.get("near");
        assertEquals(false, near.get("enabled"));
    }

    @SuppressWarnings("unchecked")
    private static IMap<Object, Object> mapWithNoNearCacheStats() {
        LocalMapStats localMapStats = proxy(LocalMapStats.class);
        return (IMap<Object, Object>) Proxy.newProxyInstance(
                IMap.class.getClassLoader(),
                new Class<?>[]{IMap.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getLocalMapStats")) {
                        return localMapStats;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static HazelcastInstance hazelcastInstanceWithMap(IMap<Object, Object> map) {
        return (HazelcastInstance) Proxy.newProxyInstance(
                HazelcastInstance.class.getClassLoader(),
                new Class<?>[]{HazelcastInstance.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getMap")) {
                        return map;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    @SuppressWarnings("unchecked")
    private static Cache<Object, Object> cacheThrowingOnUnwrap(RuntimeException exception) {
        return (Cache<Object, Object>) Proxy.newProxyInstance(
                Cache.class.getClassLoader(),
                new Class<?>[]{Cache.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("unwrap")) {
                        throw exception;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    @SuppressWarnings("unchecked")
    private static Cache<Object, Object> cacheReturningHazelcastCacheWithNoLocalStats() {
        ICache<Object, Object> hazelcastCache = proxy(ICache.class);
        return (Cache<Object, Object>) Proxy.newProxyInstance(
                Cache.class.getClassLoader(),
                new Class<?>[]{Cache.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("unwrap") && args != null && args.length == 1 && args[0] == ICache.class) {
                        return hazelcastCache;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    @SuppressWarnings("unchecked")
    private static CacheManager cacheManager(Cache<Object, Object> cache) {
        return (CacheManager) Proxy.newProxyInstance(
                CacheManager.class.getClassLoader(),
                new Class<?>[]{CacheManager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getCache")) {
                        return cache;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> defaultValue(method.getReturnType())
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType.equals(boolean.class)) {
            return false;
        }
        if (returnType.equals(int.class)) {
            return 0;
        }
        if (returnType.equals(long.class)) {
            return 0L;
        }
        if (returnType.equals(float.class)) {
            return 0.0f;
        }
        if (returnType.equals(double.class)) {
            return 0.0d;
        }
        return null;
    }
}
