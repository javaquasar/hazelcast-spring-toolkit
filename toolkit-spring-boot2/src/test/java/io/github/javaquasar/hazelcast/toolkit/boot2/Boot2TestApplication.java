package io.github.javaquasar.hazelcast.toolkit.boot2;

import io.github.javaquasar.hazelcast.toolkit.spring.test.boot.EmbeddedHazelcastTestConfiguration;
import io.github.javaquasar.hazelcast.toolkit.spring.test.boot.ListenerTestConfiguration;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntity;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntityRepository;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(
        basePackages = "io.github.javaquasar.hazelcast.toolkit.boot2",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = Boot2ListenerTestApplication.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = EmbeddedHazelcastTestConfiguration.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ListenerTestConfiguration.class)
        }
)
@EntityScan(basePackageClasses = SharedTestCachedEntity.class)
@EnableJpaRepositories(basePackageClasses = SharedTestCachedEntityRepository.class)
public class Boot2TestApplication {
}
