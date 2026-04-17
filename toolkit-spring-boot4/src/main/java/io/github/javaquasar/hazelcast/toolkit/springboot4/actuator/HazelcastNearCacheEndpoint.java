package io.github.javaquasar.hazelcast.toolkit.springboot4.actuator;

import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties;
import io.github.javaquasar.hazelcast.toolkit.spring.actuator.NearCacheProbe;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.lang.Nullable;

import java.util.Map;

/**
 * Spring Boot Actuator endpoint that probes whether the Hazelcast near-cache is
 * functioning correctly for a configured JPA entity.
 *
 * <p>This is the Spring Boot 4 / Hibernate 6 / {@code jakarta.persistence} variant.
 * The probe algorithm lives in the shared
 * {@link NearCacheProbe} helper; this class is responsible only for wiring the
 * {@code jakarta.persistence} JPA operations.
 *
 * <p>Exposed at {@code GET /actuator/hazelcast-near-cache}. Accepts optional query
 * parameters {@code entity} and {@code id} to override the values configured in
 * {@code hazelcast.toolkit.actuator.near-cache-check.*}.
 *
 * <p><b>Note:</b> Spring Boot 4.0.0 does not yet ship {@code spring-boot-actuator}.
 * This endpoint will activate automatically once Boot 4 adds Actuator support and
 * {@code spring.boot.actuate.endpoint.annotation.Endpoint} is on the classpath.
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
