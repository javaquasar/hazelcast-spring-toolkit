package io.github.javaquasar.hazelcast.toolkit.spring.cache;

import com.hazelcast.cache.impl.HazelcastServerCachingProvider;
import com.hazelcast.client.Client;
import com.hazelcast.client.cache.HazelcastClientCachingProvider;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties.Instance.Mode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class HazelcastJCacheProviderSelectionTest {

    @Test
    void selectsClientProviderFromLiveClientEndpoint() {
        assertInstanceOf(
                HazelcastClientCachingProvider.class,
                HazelcastJCacheManagerFactory.cachingProvider(
                        hazelcastInstance(endpoint(Client.class)),
                        Mode.MEMBER
                )
        );
    }

    @Test
    void selectsServerProviderFromLiveMemberEndpoint() {
        assertInstanceOf(
                HazelcastServerCachingProvider.class,
                HazelcastJCacheManagerFactory.cachingProvider(
                        hazelcastInstance(endpoint(Member.class)),
                        Mode.CLIENT
                )
        );
    }

    private static HazelcastInstance hazelcastInstance(Object endpoint) {
        return (HazelcastInstance) Proxy.newProxyInstance(
                HazelcastInstance.class.getClassLoader(),
                new Class<?>[]{HazelcastInstance.class},
                (proxy, method, args) -> method.getName().equals("getLocalEndpoint")
                        ? endpoint
                        : defaultValue(method.getReturnType())
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> T endpoint(Class<T> type) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> defaultValue(method.getReturnType())
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (type.equals(boolean.class)) {
            return false;
        }
        if (type.equals(int.class)) {
            return 0;
        }
        if (type.equals(long.class)) {
            return 0L;
        }
        return null;
    }
}
