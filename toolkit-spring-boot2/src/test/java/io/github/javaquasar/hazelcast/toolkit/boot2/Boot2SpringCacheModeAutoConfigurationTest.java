package io.github.javaquasar.hazelcast.toolkit.boot2;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.springboot2.config.HazelcastJCacheAutoConfiguration;
import io.github.javaquasar.hazelcast.toolkit.springboot2.config.HazelcastNativeSpringCacheAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.jcache.JCacheCacheManager;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class Boot2SpringCacheModeAutoConfigurationTest {

    private final HazelcastInstance hazelcastInstance = mock(HazelcastInstance.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    HazelcastJCacheAutoConfiguration.class,
                    HazelcastNativeSpringCacheAutoConfiguration.class
            ))
            .withBean(HazelcastInstance.class, () -> hazelcastInstance)
            .withBean("jCacheManager", javax.cache.CacheManager.class, () -> mock(javax.cache.CacheManager.class));

    @Test
    void defaultModeCreatesJCacheSpringCacheManager() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(javax.cache.CacheManager.class)
                .hasSingleBean(CacheManager.class)
                .getBean(CacheManager.class)
                .isInstanceOf(JCacheCacheManager.class));
    }

    @Test
    void jcacheModeCreatesJCacheSpringCacheManager() {
        contextRunner
                .withPropertyValues("hazelcast.toolkit.spring-cache.mode=jcache")
                .run(context -> assertThat(context)
                        .hasSingleBean(javax.cache.CacheManager.class)
                        .hasSingleBean(CacheManager.class)
                        .getBean(CacheManager.class)
                        .isInstanceOf(JCacheCacheManager.class));
    }

    @Test
    void nativeModeCreatesHazelcastSpringCacheManagerAroundToolkitHazelcastInstance() {
        int localHazelcastInstanceCount = Hazelcast.getAllHazelcastInstances().size();
        contextRunner
                .withPropertyValues("hazelcast.toolkit.spring-cache.mode=native")
                .run(context -> {
                    assertThat(context).hasSingleBean(javax.cache.CacheManager.class);
                    CacheManager cacheManager = context.getBean(CacheManager.class);
                    assertThat(cacheManager).isInstanceOf(com.hazelcast.spring.cache.HazelcastCacheManager.class);
                    assertThat(findWrappedHazelcastInstance(cacheManager)).isSameAs(hazelcastInstance);
                    assertThat(Hazelcast.getAllHazelcastInstances()).hasSize(localHazelcastInstanceCount);
                });
    }

    @Test
    void nativeModeKeepsExistingJCacheManagerAvailableForHibernateL2() {
        contextRunner
                .withPropertyValues("hazelcast.toolkit.spring-cache.mode=native")
                .run(context -> {
                    assertThat(context).hasSingleBean(javax.cache.CacheManager.class);
                    assertThat(context).hasSingleBean(CacheManager.class);
                    assertThat(context.getBean(CacheManager.class))
                            .isInstanceOf(com.hazelcast.spring.cache.HazelcastCacheManager.class);
                });
    }

    @Test
    void nativeModeSupportsUppercaseRelaxedBinding() {
        contextRunner
                .withPropertyValues("hazelcast.toolkit.spring-cache.mode=NATIVE")
                .run(context -> assertThat(context)
                        .hasSingleBean(CacheManager.class)
                        .getBean(CacheManager.class)
                        .isInstanceOf(com.hazelcast.spring.cache.HazelcastCacheManager.class));
    }

    @Test
    void nativeModeResolvesDynamicCacheNamesWithoutPreCreatingJCacheCache() {
        HazelcastInstance realHazelcastInstance = Hazelcast.newHazelcastInstance(testHazelcastConfig());
        int localHazelcastInstanceCount = Hazelcast.getAllHazelcastInstances().size();

        try {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            HazelcastJCacheAutoConfiguration.class,
                            HazelcastNativeSpringCacheAutoConfiguration.class
                    ))
                    .withBean(HazelcastInstance.class, () -> realHazelcastInstance)
                    .withBean("jCacheManager", javax.cache.CacheManager.class, () -> mock(javax.cache.CacheManager.class))
                    .withPropertyValues("hazelcast.toolkit.spring-cache.mode=native")
                    .run(context -> {
                        CacheManager cacheManager = context.getBean(CacheManager.class);
                        org.springframework.cache.Cache cache = cacheManager.getCache("test.dynamic.cache." + UUID.randomUUID());

                        assertThat(cache).isNotNull();
                        cache.put("key", "value");

                        assertThat(cache.get("key")).isNotNull();
                        assertThat(cache.get("key").get()).isEqualTo("value");
                        assertThat(Hazelcast.getAllHazelcastInstances()).hasSize(localHazelcastInstanceCount);
                    });
        } finally {
            realHazelcastInstance.shutdown();
        }
    }

    @Test
    void noneModeDoesNotCreateSpringCacheManager() {
        contextRunner
                .withPropertyValues("hazelcast.toolkit.spring-cache.mode=none")
                .run(context -> assertThat(context)
                        .hasSingleBean(javax.cache.CacheManager.class)
                        .doesNotHaveBean(CacheManager.class));
    }

    @Test
    void userDefinedSpringCacheManagerWins() {
        contextRunner
                .withBean("userCacheManager", CacheManager.class, ConcurrentMapCacheManager::new)
                .withPropertyValues("hazelcast.toolkit.spring-cache.mode=native")
                .run(context -> assertThat(context)
                        .hasSingleBean(CacheManager.class)
                        .getBean(CacheManager.class)
                        .isInstanceOf(ConcurrentMapCacheManager.class));
    }

    @Test
    void userDefinedSpringCacheManagerWinsInJCacheMode() {
        contextRunner
                .withBean("userCacheManager", CacheManager.class, ConcurrentMapCacheManager::new)
                .withPropertyValues("hazelcast.toolkit.spring-cache.mode=jcache")
                .run(context -> {
                    assertThat(context).hasSingleBean(javax.cache.CacheManager.class);
                    assertThat(context).hasSingleBean(CacheManager.class);
                    assertThat(context.getBean(CacheManager.class))
                            .isInstanceOf(ConcurrentMapCacheManager.class);
                });
    }

    @Test
    void noneModePreservesUserDefinedSpringCacheManager() {
        contextRunner
                .withBean("userCacheManager", CacheManager.class, ConcurrentMapCacheManager::new)
                .withPropertyValues("hazelcast.toolkit.spring-cache.mode=none")
                .run(context -> {
                    assertThat(context).hasSingleBean(javax.cache.CacheManager.class);
                    assertThat(context).hasSingleBean(CacheManager.class);
                    assertThat(context.getBean(CacheManager.class))
                            .isInstanceOf(ConcurrentMapCacheManager.class);
                });
    }

    @Test
    void nativeModeFailsFastWhenHazelcastSpringIsMissing() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("com.hazelcast.spring.cache"))
                .withPropertyValues("hazelcast.toolkit.spring-cache.mode=native")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasRootCauseMessage(
                                "hazelcast.toolkit.spring-cache.mode=native requires com.hazelcast:hazelcast-spring " +
                                        "on the application classpath because it uses " +
                                        "com.hazelcast.spring.cache.HazelcastCacheManager."
                        ));
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
        throw new AssertionError("No HazelcastInstance field found in " + target.getClass().getName());
    }

    private static Config testHazelcastConfig() {
        Config config = new Config();
        config.setClusterName("spring-cache-mode-" + UUID.randomUUID());
        config.getNetworkConfig().setPort(0).setPortAutoIncrement(true);
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        return config;
    }

}
