package io.github.javaquasar.hazelcast.toolkit.springboot2.config;

import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties;
import io.github.javaquasar.hazelcast.toolkit.springboot2.actuator.HazelcastNearCacheEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.persistence.EntityManagerFactory;

/**
 * Spring Boot 2 auto-configuration for Hazelcast Actuator endpoints.
 *
 * <p>This is the Spring Boot 2 / {@code javax.persistence} counterpart of the Boot 3
 * {@code HazelcastActuatorAutoConfiguration}.  Registers
 * {@link HazelcastNearCacheEndpoint} when all of the following conditions are met:
 * <ol>
 *   <li>{@code spring-boot-actuator} is on the classpath
 *       ({@link Endpoint} is present).</li>
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
@Configuration(proxyBeanMethods = false)
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
     * <p>Requires a {@link EntityManagerFactory} bean — this is always present when
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
