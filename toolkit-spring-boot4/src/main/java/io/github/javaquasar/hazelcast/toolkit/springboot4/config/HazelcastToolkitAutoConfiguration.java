package io.github.javaquasar.hazelcast.toolkit.springboot4.config;

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
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.cache.CacheManager;

@AutoConfiguration
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
                                               HzToolkitProperties toolkitProps) {
        return factory.createClient(
                props.getInstanceName(),
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
}
