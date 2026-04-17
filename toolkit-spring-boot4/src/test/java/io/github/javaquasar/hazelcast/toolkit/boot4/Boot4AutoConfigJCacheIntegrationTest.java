package io.github.javaquasar.hazelcast.toolkit.boot4;

import com.hazelcast.cache.HazelcastCacheManager;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.boot4.l2.Boot4L2CacheTestConfiguration;
import io.github.javaquasar.hazelcast.toolkit.spring.test.boot.SharedTestApplication;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntity;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntityRepository;
import io.github.javaquasar.hazelcast.toolkit.springboot4.config.HazelcastHibernateL2AutoConfiguration;
import io.github.javaquasar.hazelcast.toolkit.springboot4.config.HazelcastJCacheAutoConfiguration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import javax.cache.Cache;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test verifying that the Boot 4 production auto-configuration path works end-to-end:
 * <ul>
 *   <li>{@link HazelcastJCacheAutoConfiguration} creates a {@code javax.cache.CacheManager} bound
 *       to the toolkit-managed {@link HazelcastInstance} via
 *       {@code HazelcastCachingProvider.propertiesByInstanceItself(...)}</li>
 *   <li>{@link HazelcastJCacheAutoConfiguration} wraps it in a Spring {@link JCacheCacheManager}</li>
 *   <li>{@link HazelcastHibernateL2AutoConfiguration} registers a {@link HibernatePropertiesCustomizer}
 *       when {@code hazelcast.toolkit.hibernate.l2.enabled=true}</li>
 *   <li>An entity can be persisted and retrieved via the Hibernate L2 cache (end-to-end smoke-check)</li>
 * </ul>
 *
 * <p>Uses an in-process Hazelcast member (via {@link Boot4L2CacheTestConfiguration}) and an
 * H2 in-memory database — no Testcontainers required.
 *
 * <p>No test configuration wires JCache or Hibernate L2 manually — all such beans must arrive
 * exclusively from {@code META-INF/spring/AutoConfiguration.imports}.
 */
@SpringBootTest(
        classes = SharedTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:boot4autoconfig;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create",
                "spring.jpa.open-in-view=false",
                "hazelcast.toolkit.hibernate.l2.enabled=true",
                "hazelcast.toolkit.hibernate.l2.extended-config=true",
                "hazelcast.client.instance-name=boot4-autoconfig-jcache-client",
                "hazelcast.client.cluster-name=" + Boot4L2CacheTestConfiguration.CLUSTER_NAME,
                "hazelcast.client.network.smart-routing=false"
        }
)
@Import(Boot4L2CacheTestConfiguration.class)
class Boot4AutoConfigJCacheIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private javax.cache.CacheManager jCacheManager;

    @Autowired
    private CacheManager springCacheManager;

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @Autowired
    private SharedTestCachedEntityRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void clearL2CacheRegion() {
        Cache<Object, Object> cache = jCacheManager.getCache(SharedTestCachedEntity.CACHE_REGION);
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    void jCacheCacheManagerBeanIsPresentAndBoundToHazelcastInstance() {
        assertNotNull(jCacheManager, "javax.cache.CacheManager must be present");
        assertInstanceOf(
                HazelcastCacheManager.class,
                jCacheManager,
                "CacheManager must be a HazelcastCacheManager (bound via propertiesByInstanceItself)"
        );
        assertNotNull(jCacheManager.getCachingProvider(), "CachingProvider must not be null");
    }

    @Test
    void springCacheManagerBeanIsPresentAndBackedByJCache() {
        assertNotNull(springCacheManager, "Spring CacheManager must be present");
        assertInstanceOf(
                JCacheCacheManager.class,
                springCacheManager,
                "Spring CacheManager must be a JCacheCacheManager"
        );
        JCacheCacheManager jCacheCacheManager = (JCacheCacheManager) springCacheManager;
        assertInstanceOf(
                HazelcastCacheManager.class,
                jCacheCacheManager.getCacheManager(),
                "The delegate CacheManager inside JCacheCacheManager must be a HazelcastCacheManager"
        );
    }

    @Test
    void hibernatePropertiesCustomizerIsRegistered() {
        assertTrue(
                applicationContext.containsBean("hazelcastHibernateL2PropertiesCustomizer"),
                "hazelcastHibernateL2PropertiesCustomizer must be registered when hazelcast.toolkit.hibernate.l2.enabled=true"
        );
        HibernatePropertiesCustomizer customizer = applicationContext.getBean(
                "hazelcastHibernateL2PropertiesCustomizer", HibernatePropertiesCustomizer.class
        );
        assertNotNull(customizer);

        Map<String, Object> hibernateProperties = new HashMap<>();
        customizer.customize(hibernateProperties);

        assertTrue(hibernateProperties.containsKey("hibernate.cache.use_second_level_cache"),
                "Customizer must set hibernate.cache.use_second_level_cache");
        assertTrue(hibernateProperties.containsKey("hibernate.cache.region.factory_class"),
                "Customizer must set hibernate.cache.region.factory_class");
        assertInstanceOf(
                javax.cache.CacheManager.class,
                hibernateProperties.get("hibernate.javax.cache.cache_manager"),
                "hibernate.javax.cache.cache_manager must be the live CacheManager instance"
        );
    }

    @Test
    void hazelcastInstanceIsReachableAndNamed() {
        assertNotNull(hazelcastInstance, "HazelcastInstance must be present");
        assertTrue(hazelcastInstance.getLifecycleService().isRunning(),
                "HazelcastInstance must be in RUNNING state");
        assertNotNull(hazelcastInstance.getName(), "HazelcastInstance name must not be null");
    }

    @Test
    void entityIsCachedInL2AfterFirstRead() {
        Long id = transactionTemplate.execute(
                status -> repository.save(new SharedTestCachedEntity("autoconfig-boot4")).getId()
        );
        assertNotNull(id);

        transactionTemplate.execute(status -> repository.findById(id).orElseThrow());

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertTrue(
                        hazelcastInstance.getDistributedObjects().stream()
                                .anyMatch(obj -> obj.getName().contains(SharedTestCachedEntity.CACHE_REGION)),
                        "L2 cache region '" + SharedTestCachedEntity.CACHE_REGION +
                                "' must be visible as a Hazelcast distributed object after first read"
                ));
    }
}
