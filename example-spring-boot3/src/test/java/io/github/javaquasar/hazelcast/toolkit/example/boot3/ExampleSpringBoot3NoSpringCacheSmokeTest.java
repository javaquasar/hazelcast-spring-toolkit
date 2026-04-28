package io.github.javaquasar.hazelcast.toolkit.example.boot3;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("jcache")
@TestPropertySource(properties = "hazelcast.toolkit.spring-cache.mode=none")
class ExampleSpringBoot3NoSpringCacheSmokeTest extends AbstractExampleSpringBoot3SmokeTest {

    @Autowired(required = false)
    private org.springframework.cache.CacheManager springCacheManager;

    @Autowired
    private javax.cache.CacheManager jCacheManager;

    @Test
    void publishedStarterCanDisableSpringCacheManagerWhileKeepingJCacheAvailable() {
        assertThat(springCacheManager).isNull();
        assertThat(jCacheManager).isNotNull();
    }
}
