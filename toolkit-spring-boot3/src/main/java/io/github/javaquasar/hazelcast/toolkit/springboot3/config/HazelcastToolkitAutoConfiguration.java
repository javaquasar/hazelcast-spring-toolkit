package io.github.javaquasar.hazelcast.toolkit.springboot3.config;

import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.metrics.spring.HzToolkitMetricsController;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.compat.CompactClassesScanner;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.compat.IMapListenerClassesScanner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.cache.CacheManager;
import javax.cache.Caching;

@AutoConfiguration
@EnableConfigurationProperties({HazelcastClientProperties.class, HzToolkitProperties.class})
public class HazelcastToolkitAutoConfiguration {

    
//    private CacheManager.class)
//    public CacheManager cacheManager() {
//        return Caching.getCachingProvider().getCacheManager();
//    }

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
    public HazelcastClientFactory hazelcastClientFactory(CompactClassesScanner scanner) {
        return new HazelcastClientFactory(scanner);
    }

    @Bean
    @ConditionalOnMissingBean(name = "hazelcastInstance")
    public HazelcastInstance hazelcastInstance(HazelcastClientFactory factory,
                                               HazelcastClientProperties props,
                                               HzToolkitProperties toolkitProps) {
        return factory.createClient(props, toolkitProps);
    }

    @Bean
    public HzListenersAutoRegistrar hzListenersAutoRegistrar(HazelcastInstance hazelcastInstance,
                                                             IMapListenerClassesScanner scanner,
                                                             HzToolkitProperties toolkitProps) {
        // Reuse compact.basePackage as listener scan base package for now.
        return new HzListenersAutoRegistrar(hazelcastInstance, scanner, toolkitProps.getCompact().getBasePackage());
    }

    @Bean
    @ConditionalOnClass({HzToolkitMetricsController.class, CacheManager.class})
    @ConditionalOnProperty(prefix = "hazelcast.toolkit.metrics", name = "enabled", havingValue = "true")
    public HzToolkitMetricsController hzToolkitMetricsController(CacheManager cacheManager,
                                                                 HazelcastInstance hazelcastInstance) {
        return new HzToolkitMetricsController(cacheManager, hazelcastInstance);
    }

}
