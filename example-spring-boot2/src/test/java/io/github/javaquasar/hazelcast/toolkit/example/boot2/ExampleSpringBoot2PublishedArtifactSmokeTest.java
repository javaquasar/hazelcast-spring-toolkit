package io.github.javaquasar.hazelcast.toolkit.example.boot2;

import com.hazelcast.core.DistributedObject;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientFactory;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties;
import io.github.javaquasar.hazelcast.toolkit.metrics.spring.HazelcastNearCacheMetricsBinder;
import io.github.javaquasar.hazelcast.toolkit.metrics.spring.HzToolkitMetricsController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import javax.cache.CacheManager;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {
                ExampleSpringBoot2Application.class,
                ExampleSpringBoot2PublishedArtifactSmokeTest.TestInfrastructure.class
        },
        properties = {
                "spring.application.name=boot2-consumer-smoke",
                "hazelcast.toolkit.metrics.enabled=true",
                "hazelcast.toolkit.metrics.diagnostic-endpoint.enabled=true"
        }
)
class ExampleSpringBoot2PublishedArtifactSmokeTest {

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @Autowired
    private HzToolkitProperties toolkitProperties;

    @Autowired
    private HazelcastClientFactory hazelcastClientFactory;

    @Autowired
    private HazelcastNearCacheMetricsBinder nearCacheMetricsBinder;

    @Autowired
    private HzToolkitMetricsController metricsController;

    @Autowired
    private CacheManager cacheManager;

    @Test
    void startsBoot2ConsumerContextWithToolkitStarterAndMetricsDiagnostics() {
        assertThat(hazelcastInstance.getName()).isEqualTo("boot2-consumer-smoke");
        assertThat(cacheManager.getCacheNames()).isEmpty();
        assertThat(toolkitProperties).isNotNull();
        assertThat(hazelcastClientFactory).isNotNull();
        assertThat(nearCacheMetricsBinder).isNotNull();
        assertThat(metricsController.objects()).isEmpty();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestInfrastructure {

        @Bean
        HazelcastInstance hazelcastInstance() {
            return proxy(HazelcastInstance.class, (proxy, method, args) -> {
                if ("getDistributedObjects".equals(method.getName())) {
                    return Collections.<DistributedObject>emptyList();
                }
                if ("getName".equals(method.getName())) {
                    return "boot2-consumer-smoke";
                }
                return defaultValue(method.getReturnType());
            });
        }

        @Bean
        CacheManager cacheManager() {
            return proxy(CacheManager.class, (proxy, method, args) -> {
                if ("getCacheNames".equals(method.getName())) {
                    return Collections.<String>emptyList();
                }
                if ("getCache".equals(method.getName())) {
                    return null;
                }
                return defaultValue(method.getReturnType());
            });
        }

        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            Object proxy = Proxy.newProxyInstance(
                    type.getClassLoader(),
                    new Class<?>[]{type},
                    handler
            );
            return type.cast(proxy);
        }

        private static Object defaultValue(Class<?> returnType) {
            if (returnType == Boolean.TYPE) {
                return false;
            }
            if (returnType == Byte.TYPE) {
                return (byte) 0;
            }
            if (returnType == Short.TYPE) {
                return (short) 0;
            }
            if (returnType == Integer.TYPE) {
                return 0;
            }
            if (returnType == Long.TYPE) {
                return 0L;
            }
            if (returnType == Float.TYPE) {
                return 0F;
            }
            if (returnType == Double.TYPE) {
                return 0D;
            }
            if (returnType == Character.TYPE) {
                return '\0';
            }
            return null;
        }
    }
}
