package io.github.javaquasar.hazelcast.toolkit.boot2;

import io.github.javaquasar.hazelcast.toolkit.boot2.l2.Boot2L2CacheTestConfiguration;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntity;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntityRepository;
import io.github.javaquasar.hazelcast.toolkit.springboot2.actuator.HazelcastNearCacheEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test verifying the {@code /actuator/hazelcast-near-cache} endpoint
 * under the Spring Boot 2 auto-configuration stack with an embedded Hazelcast member
 * and H2 in-memory database.
 *
 * <p>The test saves a {@link SharedTestCachedEntity}, then calls
 * {@link HazelcastNearCacheEndpoint#check(String, String)} directly (no HTTP layer needed)
 * and asserts that:
 * <ul>
 *   <li>The near-cache hit is detected on the second load.</li>
 *   <li>The JPA-level eviction propagates to the near-cache (invalidation verified).</li>
 *   <li>Hibernate statistics expose the precise hit/miss deltas.</li>
 * </ul>
 */
@SpringBootTest(
        classes = Boot2TestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:boot2actuator;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.open-in-view=false",
                "hazelcast.toolkit.hibernate.l2.enabled=true",
                "hazelcast.toolkit.hibernate.l2.extended-config=true",
                "hazelcast.toolkit.hibernate.l2.use-statistics=true",
                "hazelcast.toolkit.actuator.near-cache-check.enabled=true",
                "hazelcast.client.instance-name=boot2-actuator-near-cache-client",
                // Boot2L2CacheTestConfiguration starts an embedded member under this cluster name
                // and provides a @Primary HazelcastInstance bean that connects to the correct
                // dynamic port — so the cluster-members property is effectively ignored.
                "hazelcast.client.cluster-name=boot2-l2-test-cluster",
                "hazelcast.client.network.smart-routing=false"
        }
)
@Import(Boot2L2CacheTestConfiguration.class)
class Boot2ActuatorNearCacheIntegrationTest {

    @Autowired
    private HazelcastNearCacheEndpoint nearCacheEndpoint;

    @Autowired
    private SharedTestCachedEntityRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void nearCacheEndpointBeanIsRegistered() {
        assertNotNull(nearCacheEndpoint,
                "HazelcastNearCacheEndpoint must be registered when actuator.near-cache-check.enabled=true");
    }

    @Test
    void checkReturnsOkWithHitAndInvalidationVerified() {
        Long entityId = transactionTemplate.execute(
                status -> repository.save(new SharedTestCachedEntity("actuator-probe-boot2")).getId()
        );
        assertNotNull(entityId);

        Map<String, Object> result = nearCacheEndpoint.check(
                SharedTestCachedEntity.class.getName(),
                entityId.toString()
        );

        assertEquals("OK", result.get("status"),
                "Probe must succeed — error: " + result.get("error"));
        assertEquals(SharedTestCachedEntity.class.getName(), result.get("entity"));
        assertEquals(entityId.toString(), result.get("id"));

        @SuppressWarnings("unchecked")
        Map<String, Object> nearCache = (Map<String, Object>) result.get("nearCache");
        assertNotNull(nearCache, "nearCache section must be present");
        assertTrue((Boolean) nearCache.get("hitVerified"),
                "Near-cache hit must be detected on the second load");
        assertTrue((Boolean) nearCache.get("invalidationVerified"),
                "Near-cache invalidation must be confirmed after JPA Cache.evict()");

        // With use-statistics=true, hibernateStats section must be present and show hit delta >= 1
        @SuppressWarnings("unchecked")
        Map<String, Object> hStats = (Map<String, Object>) result.get("hibernateStats");
        assertNotNull(hStats, "hibernateStats section must be present when use-statistics=true");
        assertTrue(((Number) hStats.get("l2HitsDeltaOnCachedLoad")).longValue() >= 1,
                "At least one L2 hit must be counted on the cached reload");
        assertTrue(((Number) hStats.get("l2MissesDeltaAfterEviction")).longValue() >= 1,
                "At least one L2 miss must be counted on the post-eviction reload");
        assertEquals(0L, ((Number) hStats.get("l2HitsDeltaAfterEviction")).longValue(),
                "No L2 hits should occur on the post-eviction reload (cache is cold)");
    }
}
