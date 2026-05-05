package io.github.javaquasar.hazelcast.toolkit.example.boot3;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("jcache")
class ExampleSpringBoot3JcacheSmokeTest extends AbstractExampleSpringBoot3SmokeTest {

    @Autowired
    private org.springframework.cache.CacheManager springCacheManager;

    @Autowired
    private javax.cache.CacheManager jCacheManager;

    @Test
    void publishedStarterKeepsJCacheSpringCacheModeAsDefault() {
        assertThat(springCacheManager).isInstanceOf(JCacheCacheManager.class);
        assertThat(((JCacheCacheManager) springCacheManager).getCacheManager()).isSameAs(jCacheManager);
    }
}
