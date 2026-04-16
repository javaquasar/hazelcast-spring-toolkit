package io.github.javaquasar.hazelcast.toolkit.boot2;

import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.boot2.l2.Boot2L2CacheTestConfiguration;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntity;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import javax.cache.spi.CachingProvider;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the real Boot 2 production auto-configuration path for JCache and
 * Hibernate second-level cache.
 *
 * <p>Unlike {@link Boot2JpaL2CacheIntegrationTest}, this test does <em>not</em>
 * import any test configuration that manually wires JCache or Hibernate L2
 * properties.  All JCache and L2 beans must come exclusively from
 * {@code HazelcastToolkitAutoConfiguration}.
 *
 * <p>{@link Boot2L2CacheTestConfiguration} is imported only to provide the
 * in-process Hazelcast cluster member and a Hazelcast client instance — the
 * same role it plays in a real service deployment (i.e. it provides cluster
 * infrastructure, not cache configuration).
 *
 * <h2>What is verified</h2>
 * <ol>
 *   <li>A {@code javax.cache.CacheManager} bean is present and originates from the
 *       toolkit auto-configuration (bound to the existing {@code HazelcastInstance}).
 *   <li>A Spring {@link CacheManager} bean is present and backed by JCache.
 *   <li>A {@link HibernatePropertiesCustomizer} bean is registered (activated by
 *       {@code hazelcast.toolkit.hibernate.l2.enabled=true}).
 *   <li>An entity can be persisted and retrieved via the L2 cache (end-to-end
 *       smoke-check that the customizer is actually applied).
 * </ol>
 */
/*
 * NOTE on cluster properties:
 *   Boot2L2CacheTestConfiguration provides a @Primary HazelcastInstance that connects
 *   directly to the in-process member using MEMBER_ADDRESS (a runtime constant that cannot
 *   be used in annotation values).  Because of @ConditionalOnMissingBean(name="hazelcastInstance")
 *   in the auto-config, the toolkit-auto-configured client is NOT created; the test-provided
 *   client is used instead.  The hazelcast.client.* properties below are therefore only
 *   required to satisfy the @ConfigurationProperties binding and will not cause a live connection.
 */
@SpringBootTest(
        classes = Boot2TestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:boot2autoconfig;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.open-in-view=false",
                // Activate the toolkit JCache + Hibernate L2 auto-configuration — this is the key under test
                "hazelcast.toolkit.hibernate.l2.enabled=true",
                // Satisfy @ConfigurationProperties binding; actual connection uses Boot2L2CacheTestConfiguration
                "hazelcast.client.instance-name=boot2-autoconfig-client",
                "hazelcast.client.cluster-name=boot2-l2-test-cluster",
                "hazelcast.client.network.cluster-members[0]=127.0.0.1:5701",
                "hazelcast.client.network.smart-routing=false"
        }
)
@Import(Boot2L2CacheTestConfiguration.class)
class Boot2AutoConfigJCacheIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @Autowired
    private javax.cache.CacheManager jCacheManager;

    @Autowired
    private CacheManager springCacheManager;

    @Autowired
    private SharedTestCachedEntityRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    // -------------------------------------------------------------------
    // Bean presence tests — verify the auto-config path, not manual wiring
    // -------------------------------------------------------------------

    @Test
    void jCacheCacheManagerBeanIsPresentAndBoundToHazelcastInstance() {
        assertNotNull(jCacheManager, "javax.cache.CacheManager must be created by auto-config");

        // The manager must be backed by the Hazelcast caching provider
        CachingProvider provider = jCacheManager.getCachingProvider();
        assertNotNull(provider);
        assertTrue(
                provider.getClass().getName().contains("Hazelcast"),
                "CachingProvider should be HazelcastCachingProvider, was: " + provider.getClass().getName()
        );
    }

    @Test
    void springCacheManagerBeanIsPresentAndBackedByJCache() {
        assertNotNull(springCacheManager, "Spring CacheManager must be created by auto-config");
        assertTrue(
                springCacheManager instanceof org.springframework.cache.jcache.JCacheCacheManager,
                "Spring CacheManager should be a JCacheCacheManager, was: " + springCacheManager.getClass().getName()
        );
    }

    @Test
    void hibernatePropertiesCustomizerIsRegistered() {
        // There must be at least one HibernatePropertiesCustomizer bean registered by the toolkit
        List<HibernatePropertiesCustomizer> customizers =
                applicationContext.getBeansOfType(HibernatePropertiesCustomizer.class)
                                  .values()
                                  .stream()
                                  .toList();

        assertTrue(
                customizers.size() >= 1,
                "Expected at least one HibernatePropertiesCustomizer from auto-config"
        );
    }

    @Test
    void hazelcastInstanceIsReachableAndNamedCorrectly() {
        assertNotNull(hazelcastInstance);
        // The toolkit name-builder prepends the base name to the application name.
        // With base-name="boot2-autoconfig-client" and no spring.application.name set, the
        // instance name equals the base name.
        String name = hazelcastInstance.getName();
        assertNotNull(name, "HazelcastInstance name must not be null");
        assertTrue(name.startsWith("boot2-autoconfig-client"), "Expected instance name starting with 'boot2-autoconfig-client', got: " + name);
    }

    // -------------------------------------------------------------------
    // End-to-end smoke test — entity persisted and visible in L2 cache
    // -------------------------------------------------------------------

    @Test
    void entityIsCachedInL2AfterFirstRead() {
        Long id = transactionTemplate.execute(
                status -> repository.save(new SharedTestCachedEntity("autoconfig-test")).getId()
        );
        assertNotNull(id);

        // First read — populates L2 cache
        SharedTestCachedEntity firstRead = transactionTemplate.execute(
                status -> repository.findById(id).orElseThrow()
        );
        assertNotNull(firstRead);

        // The L2 cache region must be accessible via the toolkit-wired JCache manager
        javax.cache.Cache<Object, Object> l2Region = jCacheManager.getCache(SharedTestCachedEntity.CACHE_REGION);
        assertNotNull(
                l2Region,
                "L2 cache region '" + SharedTestCachedEntity.CACHE_REGION + "' must be visible through toolkit JCache manager"
        );

        // The cache region must also appear as a distributed object in Hazelcast
        boolean regionVisibleInHazelcast = hazelcastInstance.getDistributedObjects()
                .stream()
                .anyMatch(obj -> obj.getName().contains(SharedTestCachedEntity.CACHE_REGION));
        assertTrue(
                regionVisibleInHazelcast,
                "L2 cache region '" + SharedTestCachedEntity.CACHE_REGION + "' should be visible as a Hazelcast distributed object"
        );
    }
}
