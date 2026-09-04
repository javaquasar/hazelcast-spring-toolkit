package io.github.javaquasar.hazelcast.toolkit.metrics.spring;

import com.hazelcast.cache.ICache;
import com.hazelcast.core.DistributedObject;
import com.hazelcast.core.DistributedObjectEvent;
import com.hazelcast.core.DistributedObjectListener;
import com.hazelcast.core.HazelcastInstance;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import javax.cache.Cache;
import javax.cache.CacheManager;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class HazelcastNearCacheMetricsBinderTest {

    private static final String METRIC_NAME = "hazelcast.toolkit.near.cache.enabled";
    private static final String CACHE_NAME = "test-cache";

    @Test
    void registersCacheCreatedDuringInitialScan() {
        AtomicReference<DistributedObjectListener> listener = new AtomicReference<>();
        DistributedObject cache = distributedCache(CACHE_NAME);
        AtomicBoolean eventPublished = new AtomicBoolean();
        CacheManager cacheManager = cacheManager(() -> {
            DistributedObjectListener currentListener = listener.get();
            if (currentListener != null && eventPublished.compareAndSet(false, true)) {
                currentListener.distributedObjectCreated(createdEvent(cache));
            }
            return List.of();
        });
        HazelcastNearCacheMetricsBinder binder = new HazelcastNearCacheMetricsBinder(
                cacheManager,
                hazelcastInstance(listener, List.of())
        );
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        binder.bindTo(registry);

        assertGaugeRegistered(registry);
    }

    @Test
    void registersExistingCacheWithEveryBoundRegistry() {
        AtomicReference<DistributedObjectListener> listener = new AtomicReference<>();
        HazelcastNearCacheMetricsBinder binder = new HazelcastNearCacheMetricsBinder(
                cacheManager(() -> List.of(CACHE_NAME)),
                hazelcastInstance(listener, List.of())
        );
        SimpleMeterRegistry firstRegistry = new SimpleMeterRegistry();
        SimpleMeterRegistry secondRegistry = new SimpleMeterRegistry();

        binder.bindTo(firstRegistry);
        binder.bindTo(secondRegistry);

        assertGaugeRegistered(firstRegistry);
        assertGaugeRegistered(secondRegistry);
    }

    @Test
    void registersRuntimeCacheWithEveryBoundRegistry() {
        AtomicReference<DistributedObjectListener> listener = new AtomicReference<>();
        HazelcastNearCacheMetricsBinder binder = new HazelcastNearCacheMetricsBinder(
                cacheManager(List::of),
                hazelcastInstance(listener, List.of())
        );
        SimpleMeterRegistry firstRegistry = new SimpleMeterRegistry();
        SimpleMeterRegistry secondRegistry = new SimpleMeterRegistry();
        binder.bindTo(firstRegistry);
        binder.bindTo(secondRegistry);

        listener.get().distributedObjectCreated(createdEvent(distributedCache(CACHE_NAME)));

        assertGaugeRegistered(firstRegistry);
        assertGaugeRegistered(secondRegistry);
    }

    private static void assertGaugeRegistered(MeterRegistry registry) {
        assertNotNull(registry.find(METRIC_NAME)
                .tags("cache", CACHE_NAME, "kind", "jcache")
                .gauge());
    }

    private static DistributedObjectEvent createdEvent(DistributedObject cache) {
        return new DistributedObjectEvent(
                DistributedObjectEvent.EventType.CREATED,
                cache.getServiceName(),
                cache.getName(),
                cache,
                UUID.randomUUID()
        );
    }

    @SuppressWarnings("unchecked")
    private static DistributedObject distributedCache(String cacheName) {
        return (ICache<Object, Object>) Proxy.newProxyInstance(
                ICache.class.getClassLoader(),
                new Class<?>[]{ICache.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> cacheName;
                    case "getServiceName" -> "hz:impl:cacheService";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static HazelcastInstance hazelcastInstance(
            AtomicReference<DistributedObjectListener> listener,
            Collection<DistributedObject> distributedObjects) {
        return (HazelcastInstance) Proxy.newProxyInstance(
                HazelcastInstance.class.getClassLoader(),
                new Class<?>[]{HazelcastInstance.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "addDistributedObjectListener" -> {
                        listener.set((DistributedObjectListener) args[0]);
                        yield UUID.randomUUID();
                    }
                    case "getDistributedObjects" -> distributedObjects;
                    case "removeDistributedObjectListener" -> true;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    @SuppressWarnings("unchecked")
    private static CacheManager cacheManager(Supplier<Iterable<String>> cacheNames) {
        return (CacheManager) Proxy.newProxyInstance(
                CacheManager.class.getClassLoader(),
                new Class<?>[]{CacheManager.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getCacheNames" -> cacheNames.get();
                    case "getCache" -> (Cache<Object, Object>) null;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType.equals(boolean.class)) {
            return false;
        }
        if (returnType.equals(char.class)) {
            return '\0';
        }
        return 0;
    }
}
