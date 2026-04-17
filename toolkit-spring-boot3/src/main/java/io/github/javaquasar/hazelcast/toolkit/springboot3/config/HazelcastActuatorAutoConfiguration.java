package io.github.javaquasar.hazelcast.toolkit.springboot3.config;

import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties;
import io.github.javaquasar.hazelcast.toolkit.springboot3.actuator.HazelcastNearCacheEndpoint;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for Hazelcast Actuator endpoints.
 *
 * <p>Registers {@link HazelcastNearCacheEndpoint} when all of the following conditions
 * are met:
 * <ol>
 *   <li>{@code spring-boot-actuator} is on the classpath
 *       ({@link org.springframework.boot.actuate.endpoint.annotation.Endpoint} is present).</li>
 *   <li>A JPA {@link EntityManagerFactory} bean is available in the application context.</li>
 *   <li>{@code hazelcast.toolkit.actuator.near-cache-check.enabled=true} is set.</li>
 * </ol>
 *
 * <p>The registered endpoint is exposed at {@code GET /actuator/hazelcast-near-cache}.
 *
 * <h2>Minimal configuration</h2>
 * <pre>{@code
 * hazelcast:
 *   toolkit:
 *     actuator:
 *       near-cache-check:
 *         enabled: true
 *         entity-class: com.mycompany.entity.User
 *         entity-id: "42"
 * }</pre>
 *
 * @see HazelcastNearCacheEndpoint
 * @since 0.2.0
 */
@AutoConfiguration
@AutoConfigureAfter({HazelcastToolkitAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
@ConditionalOnClass({Endpoint.class, EntityManagerFactory.class})
@ConditionalOnProperty(
        prefix  = "hazelcast.toolkit.actuator.near-cache-check",
        name    = "enabled",
        havingValue = "true"
)
public class HazelcastActuatorAutoConfiguration {

    /**
     * Registers the near-cache health-check Actuator endpoint.
     *
     * <p>Requires an {@link EntityManagerFactory} bean — this is always present when
     * Spring Data JPA or {@code spring-boot-starter-data-jpa} is on the classpath.
     *
     * @param emf        the JPA entity manager factory
     * @param properties toolkit configuration properties
     * @return the near-cache endpoint bean
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(EntityManagerFactory.class)
    public HazelcastNearCacheEndpoint hazelcastNearCacheEndpoint(
            EntityManagerFactory emf,
            HzToolkitProperties properties) {
        return new HazelcastNearCacheEndpoint(emf, properties);
    }
}
