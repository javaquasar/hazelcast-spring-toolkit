package io.github.javaquasar.hazelcast.toolkit.springboot2.config;

import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientConfigCustomizer;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientFactory;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HazelcastClientProperties;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties;
import io.github.javaquasar.hazelcast.toolkit.metrics.spring.HzToolkitMetricsController;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.compat.CompactClassesScanner;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.compat.IMapListenerClassesScanner;
import io.github.javaquasar.hazelcast.toolkit.spring.listener.HzListenersAutoRegistrar;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.cache.CacheManager;

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
    public CompactClassesScanner compactClassesScanner() {
        return new CompactClassesScanner();
    }

    @Bean
    @ConditionalOnMissingBean
    public IMapListenerClassesScanner iMapListenerClassesScanner() {
        return new IMapListenerClassesScanner();
    }

    @Bean
    @ConditionalOnMissingBean
    public HazelcastClientFactory hazelcastClientFactory(CompactClassesScanner scanner,
                                                         ObjectProvider<HazelcastClientConfigCustomizer> customizers) {
        return new HazelcastClientFactory(scanner, customizers.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean(name = "hazelcastInstance")
    public HazelcastInstance hazelcastInstance(HazelcastClientFactory factory,
                                               HazelcastClientProperties props,
                                               HzToolkitProperties toolkitProps,
                                               Environment environment) {
        return factory.createClient(
                resolveClientBaseName(props, toolkitProps),
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
    @ConditionalOnProperty(prefix = "hazelcast.toolkit.metrics", name = "enabled", havingValue = "true")
    public HzToolkitMetricsController hzToolkitMetricsController(CacheManager cacheManager,
                                                                 HazelcastInstance hazelcastInstance) {
        return new HzToolkitMetricsController(cacheManager, hazelcastInstance);
    }

    private static String resolveClientBaseName(HazelcastClientProperties props, HzToolkitProperties toolkitProps) {
        String configuredBaseName = toolkitProps.getClient().getBaseName();
        if (configuredBaseName != null && !configuredBaseName.isBlank()) {
            return configuredBaseName;
        }
        return props.getInstanceName();
    }
}
