package io.github.javaquasar.hazelcast.toolkit.example.boot3.config;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.EvictionConfig;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.MaxSizePolicy;
import com.hazelcast.config.NearCacheConfig;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookEntity;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.service.ExampleBookService;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientConfigCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExampleHazelcastConfiguration {

    @Bean
    public HazelcastClientConfigCustomizer exampleNearCacheCustomizer() {
        return this::customizeClient;
    }

    private void customizeClient(ClientConfig clientConfig) {
        clientConfig.addNearCacheConfig(nearCacheConfig(ExampleBookService.BOOKS_MAP));
        clientConfig.addNearCacheConfig(nearCacheConfig(ExampleBookService.RECOMMENDATIONS_MAP));
        clientConfig.addNearCacheConfig(nearCacheConfig(ExampleBookEntity.CACHE_REGION));
    }

    private NearCacheConfig nearCacheConfig(String name) {
        NearCacheConfig nearCacheConfig = new NearCacheConfig(name);
        nearCacheConfig.setInvalidateOnChange(true);
        nearCacheConfig.setTimeToLiveSeconds(0);
        nearCacheConfig.setMaxIdleSeconds(0);
        nearCacheConfig.setEvictionConfig(new EvictionConfig()
                .setEvictionPolicy(EvictionPolicy.LRU)
                .setMaxSizePolicy(MaxSizePolicy.ENTRY_COUNT)
                .setSize(1_000));
        return nearCacheConfig;
    }
}
