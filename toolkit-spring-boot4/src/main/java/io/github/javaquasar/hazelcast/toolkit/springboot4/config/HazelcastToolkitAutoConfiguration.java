package io.github.javaquasar.hazelcast.toolkit.springboot4.config;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spi.properties.ClusterProperty;
import com.hazelcast.spring.context.SpringManagedContext;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientConfigCustomizer;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientFactory;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastMemberConfigCustomizer;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastMemberFactory;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HazelcastConnectionSettingsResolver;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HazelcastClientProperties;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties;
import io.github.javaquasar.hazelcast.toolkit.metrics.spring.HazelcastNearCacheMetricsBinder;
import io.github.javaquasar.hazelcast.toolkit.metrics.spring.HzToolkitMetricsController;
import io.github.javaquasar.hazelcast.toolkit.scan.api.ClassScanner;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.ReflectionsClassScanner;
import io.github.javaquasar.hazelcast.toolkit.spring.listener.HzListenersAutoRegistrar;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

import javax.cache.CacheManager;

@AutoConfiguration
public class HazelcastToolkitAutoConfiguration {

    private static final String HAZELCAST_LOGGING_TYPE = "hazelcast.logging.type";

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
    @ConditionalOnMissingBean
    public HazelcastMemberFactory hazelcastMemberFactory(ClassScanner scanner,
                                                         ObjectProvider<HazelcastMemberConfigCustomizer> customizers) {
        return new HazelcastMemberFactory(scanner, customizers.orderedStream().toList());
    }

    @Bean
    public HazelcastClientConfigCustomizer hazelcastEnterpriseLicenseKeyCustomizer(
            HazelcastClientProperties props,
            HzToolkitProperties toolkitProps) {
        return clientConfig -> {
            String licenseKey = HazelcastConnectionSettingsResolver.enterpriseLicenseKey(toolkitProps, props);
            if (licenseKey != null && !licenseKey.isBlank()) {
                clientConfig.setProperty(ClusterProperty.ENTERPRISE_LICENSE_KEY.getName(), licenseKey);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(HazelcastInstance.class)
    @ConditionalOnProperty(prefix = "hazelcast.toolkit.instance", name = "mode", havingValue = "client", matchIfMissing = true)
    public HazelcastInstance hazelcastInstance(HazelcastClientFactory factory,
                                               HazelcastClientProperties props,
                                               HzToolkitProperties toolkitProps,
                                               Environment environment) {
        return factory.createClient(
                resolveClientBaseName(toolkitProps),
                resolveExplicitInstanceName(props, toolkitProps),
                environment.getProperty("spring.application.name"),
                HazelcastConnectionSettingsResolver.clusterName(
                        toolkitProps, props, HzToolkitProperties.Instance.Mode.CLIENT),
                HazelcastConnectionSettingsResolver.seedMembers(
                        toolkitProps, props, HzToolkitProperties.Instance.Mode.CLIENT),
                props.getNetwork().isSmartRouting(),
                toolkitProps.getCompact().getBasePackage()
        );
    }

    @Bean
    @ConditionalOnMissingBean(HazelcastInstance.class)
    @ConditionalOnProperty(prefix = "hazelcast.toolkit.instance", name = "mode", havingValue = "member")
    public HazelcastInstance hazelcastMemberInstance(HazelcastMemberFactory factory,
                                                     HazelcastClientProperties props,
                                                     HzToolkitProperties toolkitProps) {
        HzToolkitProperties.Member member = toolkitProps.getMember();
        HzToolkitProperties.Member.Network network = member.getNetwork();
        HzToolkitProperties.Member.Join join = network.getJoin();
        return factory.createMember(
                member.getInstanceName(),
                HazelcastConnectionSettingsResolver.clusterName(
                        toolkitProps, props, HzToolkitProperties.Instance.Mode.MEMBER),
                member.isLiteMember(),
                network.getPort(),
                network.isPortAutoIncrement(),
                network.getPublicAddress(),
                join.isAutoDetectionEnabled(),
                join.isMulticastEnabled(),
                HazelcastConnectionSettingsResolver.seedMembers(
                        toolkitProps, props, HzToolkitProperties.Instance.Mode.MEMBER),
                toolkitProps.getCompact().getBasePackage()
        );
    }

    @Bean
    @Order(0)
    @ConditionalOnClass(SpringManagedContext.class)
    @ConditionalOnMissingBean(name = "hazelcastSpringManagedContextMemberConfigCustomizer")
    public HazelcastMemberConfigCustomizer hazelcastSpringManagedContextMemberConfigCustomizer(
            ApplicationContext applicationContext) {
        return config -> {
            SpringManagedContext managedContext = new SpringManagedContext();
            managedContext.setApplicationContext(applicationContext);
            config.setManagedContext(managedContext);
        };
    }

    @Bean
    @Order(0)
    @ConditionalOnMissingBean(name = "hazelcastSlf4jLoggingMemberConfigCustomizer")
    public HazelcastMemberConfigCustomizer hazelcastSlf4jLoggingMemberConfigCustomizer() {
        return config -> {
            if (!config.getProperties().containsKey(HAZELCAST_LOGGING_TYPE)) {
                config.setProperty(HAZELCAST_LOGGING_TYPE, "slf4j");
            }
        };
    }

    @Bean
    public HazelcastMemberConfigCustomizer hazelcastMemberEnterpriseLicenseKeyCustomizer(
            HazelcastClientProperties props,
            HzToolkitProperties toolkitProps) {
        return config -> {
            String licenseKey = HazelcastConnectionSettingsResolver.enterpriseLicenseKey(toolkitProps, props);
            if (licenseKey != null && !licenseKey.isBlank()) {
                config.setProperty(ClusterProperty.ENTERPRISE_LICENSE_KEY.getName(), licenseKey);
            }
        };
    }

    @Bean
    @ConditionalOnBean(HazelcastInstance.class)
    public HzListenersAutoRegistrar hzListenersAutoRegistrar(HazelcastInstance hazelcastInstance,
                                                             ListableBeanFactory beanFactory) {
        return new HzListenersAutoRegistrar(hazelcastInstance, beanFactory);
    }

    @Bean
    @ConditionalOnBean(HazelcastInstance.class)
    @ConditionalOnClass({HzToolkitMetricsController.class, CacheManager.class})
    @ConditionalOnProperty(prefix = "hazelcast.toolkit.metrics.diagnostic-endpoint", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public HzToolkitMetricsController hzToolkitMetricsController(CacheManager cacheManager,
                                                                 HazelcastInstance hazelcastInstance) {
        return new HzToolkitMetricsController(cacheManager, hazelcastInstance);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "hazelcast.toolkit.instance",
            name = "mode",
            havingValue = "client",
            matchIfMissing = true
    )
    static class ClientNearCacheMetricsConfiguration {

        @Bean
        @ConditionalOnClass({HazelcastNearCacheMetricsBinder.class, MeterRegistry.class, CacheManager.class})
        @ConditionalOnProperty(prefix = "hazelcast.toolkit.metrics", name = "enabled", havingValue = "true")
        @ConditionalOnMissingBean
        HazelcastNearCacheMetricsBinder hazelcastNearCacheMetricsBinder(
                CacheManager cacheManager,
                HazelcastInstance hazelcastInstance) {
            return new HazelcastNearCacheMetricsBinder(cacheManager, hazelcastInstance);
        }
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
