package io.github.javaquasar.hazelcast.toolkit.springboot2.actuator;

import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties;
import io.github.javaquasar.hazelcast.toolkit.spring.actuator.NearCacheProbe;
import org.hibernate.SessionFactory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.lang.Nullable;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.util.Map;

/**
 * Spring Boot Actuator endpoint that probes whether the Hazelcast near-cache is
 * functioning correctly for a configured JPA entity.
 *
 * <p>This is the Spring Boot 2 / Hibernate 5 / {@code javax.persistence} variant.
 * The probe algorithm lives in the shared
 * {@link NearCacheProbe} helper; this class is responsible only for wiring the
 * {@code javax.persistence} JPA operations.
 *
 * <p>Exposed at {@code GET /actuator/hazelcast-near-cache}. Accepts optional query
 * parameters {@code entity} and {@code id} to override the values configured in
 * {@code hazelcast.toolkit.actuator.near-cache-check.*}.
 *
 * <h2>What is verified</h2>
 * <ol>
 *   <li><b>L2 / near-cache hit</b> — the entity is loaded twice in separate persistence
 *       contexts. The second load must be served from the L2 / near-cache (detected via
 *       Hibernate statistics if enabled, or sub-millisecond timing as a fallback).</li>
 *   <li><b>Invalidation propagation</b> — the entity is evicted from the L2 cache via
 *       the JPA {@link javax.persistence.Cache} API. A third load must go to the
 *       database, confirming that the eviction reached the near-cache on this client.</li>
 * </ol>
 *
 * @see NearCacheProbe
 * @since 0.2.0
 */
@Endpoint(id = "hazelcastNearCache")
public class HazelcastNearCacheEndpoint {

    private final EntityManagerFactory emf;
    private final NearCacheProbe probe;

    /**
     * Creates the endpoint.
     *
     * @param emf        the JPA entity manager factory; used for entity lookups and
     *                   L2 cache eviction
     * @param properties toolkit properties; supplies the default probe entity and id
     */
    public HazelcastNearCacheEndpoint(EntityManagerFactory emf,
                                      HzToolkitProperties properties) {
        this.emf = emf;
        this.probe = new NearCacheProbe(properties);
    }

    /**
     * Runs the near-cache probe and returns a structured JSON result.
     *
     * <p>Query parameters override the values from
     * {@code hazelcast.toolkit.actuator.near-cache-check.*}.
     *
     * @param entity fully-qualified JPA entity class name (optional override)
     * @param id     primary-key value as a string (optional override)
     * @return a map that Spring Boot Actuator serializes to JSON
     */
    @ReadOperation
    public Map<String, Object> check(@Nullable String entity, @Nullable String id) {
        return probe.check(
                entity, id,
                () -> emf.unwrap(SessionFactory.class).getStatistics(),
                this::findInNewContext,
                (cls, entityId) -> emf.getCache().evict(cls, entityId)
        );
    }

    /**
     * Loads an entity in a short-lived, dedicated {@link EntityManager} so the
     * first-level (session-level) cache never interferes with consecutive loads.
     *
     * <p>Each call opens and closes its own EntityManager and transaction, ensuring
     * that only the second-level cache (and near-cache) can satisfy subsequent finds.
     */
    private Object findInNewContext(Class<?> entityClass, Object entityId) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            Object entity = em.find(entityClass, entityId);
            em.getTransaction().commit();
            return entity;
        } catch (Exception ex) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw ex;
        } finally {
            em.close();
        }
    }
}
