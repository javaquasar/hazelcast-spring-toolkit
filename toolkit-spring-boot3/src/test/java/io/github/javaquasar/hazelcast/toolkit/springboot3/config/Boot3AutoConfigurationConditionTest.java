package io.github.javaquasar.hazelcast.toolkit.springboot3.config;

import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties;
import io.github.javaquasar.hazelcast.toolkit.metrics.spring.HazelcastNearCacheMetricsBinder;
import io.github.javaquasar.hazelcast.toolkit.metrics.spring.HibernateL2MetricsBinder;
import io.github.javaquasar.hazelcast.toolkit.metrics.spring.HzToolkitMetricsController;
import io.github.javaquasar.hazelcast.toolkit.springboot3.actuator.HazelcastNearCacheEndpoint;
import io.github.javaquasar.hazelcast.toolkit.springboot3.actuator.HazelcastToolkitHealthIndicator;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.cache.CacheManager;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Boot3AutoConfigurationConditionTest {

    private final ApplicationContextRunner toolkitContext = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    HazelcastToolkitAutoConfiguration.class,
                    HazelcastHibernateL2AutoConfiguration.class
            ))
            .withUserConfiguration(TestInfrastructure.class);

    private final ApplicationContextRunner actuatorContext = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    HazelcastToolkitAutoConfiguration.class,
                    HazelcastActuatorAutoConfiguration.class
            ))
            .withUserConfiguration(TestInfrastructure.class);

    private final ApplicationContextRunner healthContext = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    HazelcastToolkitAutoConfiguration.class,
                    HazelcastHealthAutoConfiguration.class
            ))
            .withUserConfiguration(TestInfrastructure.class);

    @Test
    void diagnosticControllerIsControlledSeparatelyFromMicrometerMetrics() {
        toolkitContext
                .withPropertyValues("hazelcast.toolkit.metrics.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(HzToolkitMetricsController.class));

        toolkitContext
                .withPropertyValues("hazelcast.toolkit.metrics.diagnostic-endpoint.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(HzToolkitMetricsController.class));
    }

    @Test
    void metricsBindersDoNotRequireMeterRegistryAtBeanCreationTime() {
        toolkitContext
                .withPropertyValues(
                        "hazelcast.toolkit.metrics.enabled=true",
                        "hazelcast.toolkit.hibernate.l2.enabled=true"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(HazelcastNearCacheMetricsBinder.class);
                    assertThat(context).hasSingleBean(HibernateL2MetricsBinder.class);
                });
    }

    @Test
    void hibernateL2CustomizerRequiresExplicitProperty() {
        toolkitContext
                .run(context -> assertThat(context).doesNotHaveBean(HibernatePropertiesCustomizer.class));

        toolkitContext
                .withPropertyValues("hazelcast.toolkit.hibernate.l2.enabled=true")
                .run(context -> assertThat(context)
                        .hasBean("hazelcastHibernateL2PropertiesCustomizer"));
    }

    @Test
    void extendedJcacheHibernateModeFailsWhenCacheManagerIsUnavailable() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        HazelcastToolkitAutoConfiguration.class,
                        HazelcastHibernateL2AutoConfiguration.class
                ))
                .withUserConfiguration(HazelcastOnlyInfrastructure.class)
                .withPropertyValues(
                        "hazelcast.toolkit.hibernate.l2.enabled=true",
                        "hazelcast.toolkit.hibernate.l2.extended-config=true",
                        "hazelcast.toolkit.hibernate.l2.region-factory=JCACHE"
                )
                .run(context -> {
                    HibernatePropertiesCustomizer customizer = context.getBean(
                            "hazelcastHibernateL2PropertiesCustomizer",
                            HibernatePropertiesCustomizer.class
                    );

                    assertThatThrownBy(() -> customizer.customize(new LinkedHashMap<>()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("requires a javax.cache.CacheManager bean");
                });
    }

    @Test
    void nativeHibernateModeFailsFastWhenHazelcastHibernateIsMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        HazelcastToolkitAutoConfiguration.class,
                        HazelcastHibernateL2AutoConfiguration.class
                ))
                .withUserConfiguration(HazelcastOnlyInfrastructure.class)
                .withClassLoader(new FilteredClassLoader("com.hazelcast.hibernate"))
                .withPropertyValues(
                        "hazelcast.toolkit.hibernate.l2.enabled=true",
                        "hazelcast.toolkit.hibernate.l2.region-factory=HAZELCAST_LOCAL"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("com.hazelcast.hibernate.HazelcastLocalCacheRegionFactory")
                            .hasMessageContaining("com.hazelcast:hazelcast-hibernate");
                });
    }

    @Test
    void nativeHibernateModeFallsBackToActualHazelcastInstanceNameForBrokenNativeClientName() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        HazelcastToolkitAutoConfiguration.class,
                        HazelcastHibernateL2AutoConfiguration.class
                ))
                .withUserConfiguration(HazelcastOnlyInfrastructure.class)
                .withPropertyValues(
                        "hazelcast.toolkit.hibernate.l2.enabled=true",
                        "hazelcast.toolkit.hibernate.l2.region-factory=HAZELCAST"
                )
                .run(context -> {
                    HibernatePropertiesCustomizer customizer = context.getBean(
                            "hazelcastHibernateL2PropertiesCustomizer",
                            HibernatePropertiesCustomizer.class
                    );
                    LinkedHashMap<String, Object> hibernateProperties = new LinkedHashMap<>();
                    hibernateProperties.put("hibernate.cache.hazelcast.use_native_client", "true");
                    hibernateProperties.put("hibernate.cache.hazelcast.native_client_instance_name", "app-hz-client-");

                    customizer.customize(hibernateProperties);

                    assertThat(hibernateProperties)
                            .containsEntry("hibernate.cache.hazelcast.native_client_instance_name", "test-hazelcast-instance")
                            .containsEntry("hazelcast.instance.name", "test-hazelcast-instance");
                });
    }

    @Test
    void nativeHibernateModeFallsBackEvenWhenRegionFactoryIsAlreadyConfigured() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        HazelcastToolkitAutoConfiguration.class,
                        HazelcastHibernateL2AutoConfiguration.class
                ))
                .withUserConfiguration(HazelcastOnlyInfrastructure.class)
                .withPropertyValues(
                        "hazelcast.toolkit.hibernate.l2.enabled=true",
                        "hazelcast.toolkit.hibernate.l2.region-factory=HAZELCAST"
                )
                .run(context -> {
                    HibernatePropertiesCustomizer customizer = context.getBean(
                            "hazelcastHibernateL2PropertiesCustomizer",
                            HibernatePropertiesCustomizer.class
                    );
                    LinkedHashMap<String, Object> hibernateProperties = new LinkedHashMap<>();
                    hibernateProperties.put("hibernate.cache.region.factory_class", "com.hazelcast.hibernate.HazelcastCacheRegionFactory");
                    hibernateProperties.put("hibernate.cache.hazelcast.use_native_client", "true");
                    hibernateProperties.put("hibernate.cache.hazelcast.native_client_instance_name", "app-hz-client-");

                    customizer.customize(hibernateProperties);

                    assertThat(hibernateProperties)
                            .containsEntry("hibernate.cache.hazelcast.native_client_instance_name", "test-hazelcast-instance");
                });
    }

    @Test
    void actuatorEndpointRequiresExplicitPropertyAndEntityManagerFactory() {
        actuatorContext
                .run(context -> assertThat(context).doesNotHaveBean(HazelcastNearCacheEndpoint.class));

        actuatorContext
                .withPropertyValues("hazelcast.toolkit.actuator.near-cache-check.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(HazelcastNearCacheEndpoint.class));
    }

    @Test
    void actuatorEndpointBacksOffWithoutEntityManagerFactoryBean() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        HazelcastToolkitAutoConfiguration.class,
                        HazelcastHibernateL2AutoConfiguration.class,
                        HazelcastActuatorAutoConfiguration.class
                ))
                .withUserConfiguration(HazelcastOnlyInfrastructure.class)
                .withPropertyValues("hazelcast.toolkit.actuator.near-cache-check.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(HazelcastNearCacheEndpoint.class));
    }

    @Test
    void healthIndicatorRequiresExplicitPropertyAndHazelcastInstance() {
        healthContext
                .run(context -> assertThat(context).doesNotHaveBean(HazelcastToolkitHealthIndicator.class));

        healthContext
                .withPropertyValues("hazelcast.toolkit.health.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(HazelcastToolkitHealthIndicator.class));

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(HazelcastHealthAutoConfiguration.class))
                .withPropertyValues("hazelcast.toolkit.health.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(HazelcastToolkitHealthIndicator.class));
    }

    @Test
    void userDefinedBeansTakePrecedence() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        HazelcastToolkitAutoConfiguration.class,
                        HazelcastActuatorAutoConfiguration.class
                ))
                .withUserConfiguration(TestInfrastructure.class, UserDefinedBeans.class)
                .withPropertyValues(
                        "hazelcast.toolkit.metrics.enabled=true",
                        "hazelcast.toolkit.hibernate.l2.enabled=true",
                        "hazelcast.toolkit.health.enabled=true",
                        "hazelcast.toolkit.metrics.diagnostic-endpoint.enabled=true",
                        "hazelcast.toolkit.actuator.near-cache-check.enabled=true"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(HazelcastNearCacheEndpoint.class);
                    assertThat(context).hasSingleBean(HazelcastToolkitHealthIndicator.class);
                    assertThat(context).hasSingleBean(HazelcastNearCacheMetricsBinder.class);
                    assertThat(context).hasSingleBean(HibernateL2MetricsBinder.class);
                    assertThat(context).hasSingleBean(HzToolkitMetricsController.class);

                    assertThat(context.getBean(HazelcastNearCacheEndpoint.class))
                            .isSameAs(context.getBean("customHazelcastNearCacheEndpoint"));
                    assertThat(context.getBean(HazelcastToolkitHealthIndicator.class))
                            .isSameAs(context.getBean("customHazelcastToolkitHealthIndicator"));
                    assertThat(context.getBean(HazelcastNearCacheMetricsBinder.class))
                            .isSameAs(context.getBean("customHazelcastNearCacheMetricsBinder"));
                    assertThat(context.getBean(HibernateL2MetricsBinder.class))
                            .isSameAs(context.getBean("customHibernateL2MetricsBinder"));
                    assertThat(context.getBean(HzToolkitMetricsController.class))
                            .isSameAs(context.getBean("customHzToolkitMetricsController"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestInfrastructure extends HazelcastOnlyInfrastructure {

        @Bean
        CacheManager cacheManager() {
            return proxy(CacheManager.class);
        }

        @Bean
        EntityManagerFactory entityManagerFactory() {
            return proxy(EntityManagerFactory.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class HazelcastOnlyInfrastructure {

        @Bean
        HazelcastInstance hazelcastInstance() {
            return proxy(HazelcastInstance.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    static class UserDefinedBeans {

        @Bean
        HazelcastNearCacheEndpoint customHazelcastNearCacheEndpoint(EntityManagerFactory entityManagerFactory) {
            return new HazelcastNearCacheEndpoint(entityManagerFactory, new HzToolkitProperties());
        }

        @Bean
        HazelcastNearCacheMetricsBinder customHazelcastNearCacheMetricsBinder(
                CacheManager cacheManager,
                HazelcastInstance hazelcastInstance) {
            return new HazelcastNearCacheMetricsBinder(cacheManager, hazelcastInstance);
        }

        @Bean
        HibernateL2MetricsBinder customHibernateL2MetricsBinder(EntityManagerFactory entityManagerFactory) {
            return new HibernateL2MetricsBinder(entityManagerFactory, new HzToolkitProperties());
        }

        @Bean
        HzToolkitMetricsController customHzToolkitMetricsController(
                CacheManager cacheManager,
                HazelcastInstance hazelcastInstance) {
            return new HzToolkitMetricsController(cacheManager, hazelcastInstance);
        }

        @Bean
        HazelcastToolkitHealthIndicator customHazelcastToolkitHealthIndicator(HazelcastInstance hazelcastInstance) {
            return new HazelcastToolkitHealthIndicator(hazelcastInstance);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    if (method.getName().equals("toString")) {
                        return "test-" + type.getSimpleName();
                    }
                    if (method.getName().equals("getName")) {
                        return "test-hazelcast-instance";
                    }
                    if (method.getName().equals("getCacheNames")) {
                        return Collections.emptyList();
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    if (method.getReturnType().equals(int.class)) {
                        return 0;
                    }
                    if (method.getReturnType().equals(long.class)) {
                        return 0L;
                    }
                    return null;
                }
        );
    }
}
