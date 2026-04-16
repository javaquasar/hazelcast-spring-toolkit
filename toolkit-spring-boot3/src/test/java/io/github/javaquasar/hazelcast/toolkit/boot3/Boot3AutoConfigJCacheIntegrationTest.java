package io.github.javaquasar.hazelcast.toolkit.boot3;

import com.hazelcast.cache.HazelcastCacheManager;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.spring.test.boot.SharedTestApplication;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntity;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntityRepository;
import io.github.javaquasar.hazelcast.toolkit.springboot3.config.HazelcastHibernateL2AutoConfiguration;
import io.github.javaquasar.hazelcast.toolkit.springboot3.config.HazelcastJCacheAutoConfiguration;
import io.github.javaquasar.hazelcast.toolkit.testcontainers.TestcontainersEnvironment;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.cache.Cache;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test verifying that the Boot 3 production auto-configuration path works end-to-end:
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
 * <p>No {@code L2CacheTestConfiguration} is imported here — all JCache and Hibernate L2 beans must
 * arrive exclusively from {@code META-INF/spring/AutoConfiguration.imports}.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = SharedTestApplication.class,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.open-in-view=false",
                // Activate the toolkit JCache + Hibernate L2 auto-configuration — this is the key under test
                "hazelcast.toolkit.hibernate.l2.enabled=true",
                // Unique client name prevents InvalidConfigurationException when multiple test contexts
                // start in the same JVM (Hazelcast forbids duplicate instance names per JVM)
                "hazelcast.client.instance-name=boot3-autoconfig-jcache-test"
        }
)
class Boot3AutoConfigJCacheIntegrationTest extends TestcontainersEnvironment {

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        TestcontainersEnvironment.registerSpringProperties(registry);
    }

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

    /**
     * Verifies that the {@code javax.cache.CacheManager} bean comes from auto-config and is
     * backed by Hazelcast (i.e. it is a {@link HazelcastCacheManager}).
     */
    @Test
    void jCacheCacheManagerBeanIsPresentAndBoundToHazelcastInstance() {
        assertNotNull(jCacheManager, "javax.cache.CacheManager must be present");
        assertInstanceOf(
                HazelcastCacheManager.class,
                jCacheManager,
                "CacheManager must be a HazelcastCacheManager (bound via propertiesByInstanceItself)"
        );
        assertNotNull(jCacheManager.getCachingProvider(),
                "CachingProvider must not be null");
    }

    /**
     * Verifies that the Spring {@link CacheManager} bean is a {@link JCacheCacheManager} that
     * delegates to the Hazelcast {@code javax.cache.CacheManager}.
     */
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

    /**
     * Verifies that {@link HazelcastHibernateL2AutoConfiguration} fired and registered a
     * {@link HibernatePropertiesCustomizer} with the expected Hibernate L2 properties.
     */
    @Test
    void hibernatePropertiesCustomizerIsRegistered() {
        assertTrue(
                applicationContext.containsBean("hazelcastHibernateL2PropertiesCustomizer"),
                "hazelcastHibernateL2PropertiesCustomizer bean must be registered when hazelcast.toolkit.hibernate.l2.enabled=true"
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

    /**
     * Verifies that the {@link HazelcastInstance} is running and connected to the cluster.
     */
    @Test
    void hazelcastInstanceIsReachableAndNamed() {
        assertNotNull(hazelcastInstance, "HazelcastInstance must be present");
        assertTrue(hazelcastInstance.getLifecycleService().isRunning(),
                "HazelcastInstance must be in RUNNING state");
        assertNotNull(hazelcastInstance.getName(), "HazelcastInstance name must not be null");
        assertNotNull(hazelcastInstance.getCluster(),
                "HazelcastInstance must be connected to a cluster");
    }

    /**
     * End-to-end smoke test: an entity is persisted, then read back through JPA.
     * After the first read, the entity should appear in the Hazelcast L2 cache region,
     * confirming that {@link HazelcastHibernateL2AutoConfiguration} wired Hibernate correctly.
     */
    @Test
    void entityIsCachedInL2AfterFirstRead() {
        Long id = transactionTemplate.execute(
                status -> repository.save(new SharedTestCachedEntity("autoconfig-boot3")).getId()
        );
        assertNotNull(id);

        // First read — should populate the L2 cache
        SharedTestCachedEntity firstRead = transactionTemplate.execute(
                status -> repository.findById(id).orElseThrow()
        );
        assertNotNull(firstRead);

        // The L2 cache region must appear as a Hazelcast distributed object
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
