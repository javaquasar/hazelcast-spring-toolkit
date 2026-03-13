package io.github.javaquasar.hazelcast.toolkit.spring.test.boot;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class EmbeddedHazelcastTestConfiguration {

    @Bean(destroyMethod = "shutdown")
    public HazelcastInstance hazelcastInstance() {
        Config config = new Config();
        config.setClusterName("shared-listener-test-cluster");
        config.setProperty("hazelcast.logging.type", "slf4j");
        return Hazelcast.newHazelcastInstance(config);
    }
}
