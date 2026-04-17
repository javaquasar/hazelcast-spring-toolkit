package io.github.javaquasar.hazelcast.toolkit.boot3;

import io.github.javaquasar.hazelcast.toolkit.boot3.l2.L2CacheTestConfiguration;
import io.github.javaquasar.hazelcast.toolkit.spring.test.boot.SharedTestApplication;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntity;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntityRepository;
import io.github.javaquasar.hazelcast.toolkit.springboot3.actuator.HazelcastNearCacheEndpoint;
import io.github.javaquasar.hazelcast.toolkit.testcontainers.TestcontainersEnvironment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test verifying the {@code /actuator/hazelcast-near-cache} endpoint
 * under the Spring Boot 3 auto-configuration stack with a real Hazelcast cluster
 * and Postgres database (via Testcontainers).
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
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = SharedTestApplication.class,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create",
                "spring.jpa.open-in-view=false",
                "hazelcast.toolkit.hibernate.l2.enabled=true",
                "hazelcast.toolkit.hibernate.l2.extended-config=true",
                "hazelcast.toolkit.hibernate.l2.use-statistics=true",
                "hazelcast.toolkit.actuator.near-cache-check.enabled=true",
                // Unique client name prevents InvalidConfigurationException when multiple test contexts
                // start in the same JVM (Hazelcast forbids duplicate instance names per JVM)
                "hazelcast.client.instance-name=boot3-actuator-near-cache-test"
        }
)
@Import(L2CacheTestConfiguration.class)
class Boot3ActuatorNearCacheIntegrationTest extends TestcontainersEnvironment {

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        TestcontainersEnvironment.registerSpringProperties(registry);
    }

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
                status -> repository.save(new SharedTestCachedEntity("actuator-probe-boot3")).getId()
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
