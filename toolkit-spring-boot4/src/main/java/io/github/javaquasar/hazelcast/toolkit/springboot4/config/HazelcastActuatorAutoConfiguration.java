package io.github.javaquasar.hazelcast.toolkit.springboot4.config;

import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties;
import io.github.javaquasar.hazelcast.toolkit.springboot4.actuator.HazelcastNearCacheEndpoint;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for Hazelcast Actuator endpoints.
 *
 * <p>Registers {@link HazelcastNearCacheEndpoint} when all of the following conditions
 * are met:
 * <ol>
 *   <li>{@code spring-boot-actuator} is on the classpath
 *       ({@link Endpoint} is present).</li>
 *   <li>A JPA {@link EntityManagerFactory} bean is available in the application context.</li>
 *   <li>{@code hazelcast.toolkit.actuator.near-cache-check.enabled=true} is set.</li>
 * </ol>
 *
 * <p><b>Note:</b> Spring Boot 4.0.0 does not yet ship {@code spring-boot-actuator} or
 * {@code HibernateJpaAutoConfiguration}. Both conditions will evaluate to {@code false}
 * and this configuration will be a no-op until Boot 4 adds those modules.
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

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(EntityManagerFactory.class)
    public HazelcastNearCacheEndpoint hazelcastNearCacheEndpoint(
            EntityManagerFactory emf,
            HzToolkitProperties properties) {
        return new HazelcastNearCacheEndpoint(emf, properties);
    }
}
