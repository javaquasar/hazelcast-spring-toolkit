package io.github.javaquasar.hazelcast.toolkit.boot2;

import io.github.javaquasar.hazelcast.toolkit.boot2.l2.Boot2L2CacheTestConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
        basePackages = "io.github.javaquasar.hazelcast.toolkit.boot2",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = Boot2TestApplication.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = Boot2L2CacheTestConfiguration.class)
        }
)
public class Boot2ListenerTestApplication {
}
