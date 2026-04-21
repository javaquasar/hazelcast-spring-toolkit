package io.github.javaquasar.hazelcast.toolkit.springboot2.config;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spi.properties.ClusterProperty;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientConfigCustomizer;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientFactory;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HazelcastClientProperties;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties;
import io.github.javaquasar.hazelcast.toolkit.metrics.spring.HazelcastNearCacheMetricsBinder;
import io.github.javaquasar.hazelcast.toolkit.metrics.spring.HibernateL2MetricsBinder;
import io.github.javaquasar.hazelcast.toolkit.metrics.spring.HzToolkitMetricsController;
import io.github.javaquasar.hazelcast.toolkit.scan.api.ClassScanner;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.ReflectionsClassScanner;
import io.github.javaquasar.hazelcast.toolkit.spring.listener.HzListenersAutoRegistrar;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.cache.CacheManager;
import javax.persistence.EntityManagerFactory;

/**
 * Core Boot 2 auto-configuration for the Hazelcast toolkit.
 *
 * <p>Provides:
 * <ul>
 *   <li>Property-binding beans ({@link HazelcastClientProperties}, {@link HzToolkitProperties})
 *   <li>A configured {@link HazelcastInstance} Hazelcast client
 *   <li>Automatic {@code @HzIMapListener} registration
 *   <li>Optional metrics REST controller
 * </ul>
 *
 * <p>JCache and Hibernate second-level cache auto-configuration are handled by the
 * companion classes {@link HazelcastJCacheAutoConfiguration} and
 * {@link HazelcastHibernateL2AutoConfiguration}, both of which are registered in
 * {@code META-INF/spring.factories} and ordered to run after this class.
 */
@Configuration(proxyBeanMethods = false)
public class HazelcastToolkitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConfigurationProperties(prefix = "hazelcast.client")
    public HazelcastClientProperties hazelcastClientProperties() {
        return new HazelcastClientProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConfigurationProperties(prefix = "hazelcast.toolkit")
    public HzToolkitProperties hzToolkitProperties() {
        return new HzToolkitProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    public ClassScanner classScanner() {
        return new ReflectionsClassScanner();
    }

    @Bean
    @ConditionalOnMissingBean
    public HazelcastClientFactory hazelcastClientFactory(ClassScanner scanner,
                                                         ObjectProvider<HazelcastClientConfigCustomizer> customizers) {
        return new HazelcastClientFactory(scanner, customizers.orderedStream().toList());
    }

    @Bean
    @ConditionalOnProperty(prefix = "hazelcast.client", name = "enterprise-license-key")
    public HazelcastClientConfigCustomizer hazelcastEnterpriseLicenseKeyCustomizer(HazelcastClientProperties props) {
        return clientConfig -> {
            String licenseKey = props.getEnterpriseLicenseKey();
            if (licenseKey != null && !licenseKey.isBlank()) {
                clientConfig.setProperty(ClusterProperty.ENTERPRISE_LICENSE_KEY.getName(), licenseKey);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(name = "hazelcastInstance")
    public HazelcastInstance hazelcastInstance(HazelcastClientFactory factory,
                                               HazelcastClientProperties props,
                                               HzToolkitProperties toolkitProps,
                                               Environment environment) {
        return factory.createClient(
                resolveClientBaseName(toolkitProps),
                resolveExplicitInstanceName(props, toolkitProps),
                environment.getProperty("spring.application.name"),
                props.getClusterName(),
                props.getNetwork().getClusterMembers(),
                props.getNetwork().isSmartRouting(),
                toolkitProps.getCompact().getBasePackage()
        );
    }

    @Bean
    public HzListenersAutoRegistrar hzListenersAutoRegistrar(HazelcastInstance hazelcastInstance,
                                                             ListableBeanFactory beanFactory) {
        return new HzListenersAutoRegistrar(hazelcastInstance, beanFactory);
    }

    @Bean
    @ConditionalOnClass({HzToolkitMetricsController.class, CacheManager.class})
    @ConditionalOnProperty(prefix = "hazelcast.toolkit.metrics.diagnostic-endpoint", name = "enabled", havingValue = "true")
    public HzToolkitMetricsController hzToolkitMetricsController(CacheManager cacheManager,
                                                                 HazelcastInstance hazelcastInstance) {
        return new HzToolkitMetricsController(cacheManager, hazelcastInstance);
    }

    @Bean
    @ConditionalOnClass({HazelcastNearCacheMetricsBinder.class, MeterRegistry.class, CacheManager.class})
    @ConditionalOnProperty(prefix = "hazelcast.toolkit.metrics", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public HazelcastNearCacheMetricsBinder hazelcastNearCacheMetricsBinder(
            MeterRegistry meterRegistry,
            CacheManager cacheManager,
            HazelcastInstance hazelcastInstance) {
        HazelcastNearCacheMetricsBinder binder = new HazelcastNearCacheMetricsBinder(cacheManager, hazelcastInstance);
        binder.bindTo(meterRegistry);
        return binder;
    }

    @Bean
    @ConditionalOnClass({HibernateL2MetricsBinder.class, MeterRegistry.class, EntityManagerFactory.class})
    @ConditionalOnProperty(prefix = "hazelcast.toolkit.metrics", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public HibernateL2MetricsBinder hibernateL2MetricsBinder(
            MeterRegistry meterRegistry,
            EntityManagerFactory entityManagerFactory,
            HzToolkitProperties properties) {
        HibernateL2MetricsBinder binder = new HibernateL2MetricsBinder(entityManagerFactory, properties);
        binder.bindTo(meterRegistry);
        return binder;
    }

    private static String resolveClientBaseName(HzToolkitProperties toolkitProps) {
        return toolkitProps.getClient().getBaseName();
    }

    private static String resolveExplicitInstanceName(HazelcastClientProperties props, HzToolkitProperties toolkitProps) {
        String configuredBaseName = toolkitProps.getClient().getBaseName();
        if (configuredBaseName != null && !configuredBaseName.isBlank()) {
            return null;
        }
        return props.getInstanceName();
    }
}
