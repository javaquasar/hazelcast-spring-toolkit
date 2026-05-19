package io.github.javaquasar.hazelcast.toolkit.example.boot2;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("legacy-native-cache")
@SpringBootTest(
        classes = {
                ExampleSpringBoot2Application.class,
                ExampleSpringBoot2PublishedArtifactSmokeTest.TestInfrastructure.class
        }
)
@TestPropertySource(properties = "hazelcast.toolkit.spring-cache.mode=native")
class ExampleSpringBoot2LegacyNativeCacheSmokeTest {

    @Autowired
    private CacheManager springCacheManager;

    @Autowired
    private javax.cache.CacheManager jCacheManager;

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @Test
    void boot2LegacyServiceCanUseNativeSpringCacheModeAndKeepJCacheAvailable() {
        assertThat(springCacheManager).isInstanceOf(com.hazelcast.spring.cache.HazelcastCacheManager.class);
        assertThat(jCacheManager).isNotNull();
        assertThat(findWrappedHazelcastInstance(springCacheManager)).isSameAs(hazelcastInstance);
        assertThat(Hazelcast.getAllHazelcastInstances()).isEmpty();
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
