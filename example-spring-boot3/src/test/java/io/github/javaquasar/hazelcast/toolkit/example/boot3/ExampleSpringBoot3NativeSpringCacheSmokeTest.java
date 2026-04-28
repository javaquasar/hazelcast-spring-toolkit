package io.github.javaquasar.hazelcast.toolkit.example.boot3;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("jcache")
@TestPropertySource(properties = "hazelcast.toolkit.spring-cache.mode=native")
class ExampleSpringBoot3NativeSpringCacheSmokeTest extends AbstractExampleSpringBoot3SmokeTest {

    @Autowired
    private org.springframework.cache.CacheManager springCacheManager;

    @Autowired
    private javax.cache.CacheManager jCacheManager;

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @Test
    void publishedStarterCanUseNativeSpringCacheModeWithToolkitClient() {
        assertThat(springCacheManager).isInstanceOf(com.hazelcast.spring.cache.HazelcastCacheManager.class);
        assertThat(jCacheManager).isNotNull();
        assertThat(findWrappedHazelcastInstance(springCacheManager)).isSameAs(hazelcastInstance);
        assertThat(Hazelcast.getAllHazelcastInstances()).hasSize(1);

        Cache dynamicCache = springCacheManager.getCache("example.post.release.dynamic." + System.nanoTime());
        assertThat(dynamicCache).isNotNull();

        dynamicCache.put("key", "value");
        assertThat(dynamicCache.get("key", String.class)).isEqualTo("value");
    }

    private static HazelcastInstance findWrappedHazelcastInstance(Object target) {
        Class<?> type = target.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (HazelcastInstance.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        return (HazelcastInstance) field.get(target);
                    } catch (IllegalAccessException ex) {
                        throw new IllegalStateException("Could not inspect HazelcastCacheManager", ex);
                    }
                }
            }
            type = type.getSuperclass();
        }
        throw new IllegalStateException("Could not find wrapped HazelcastInstance in " + target.getClass());
    }
}
