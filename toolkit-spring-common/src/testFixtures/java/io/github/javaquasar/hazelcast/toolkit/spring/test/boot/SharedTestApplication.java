package io.github.javaquasar.hazelcast.toolkit.spring.test.boot;

import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntity;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2.SharedTestCachedEntityRepository;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackageClasses = SharedTestCachedEntity.class)
@EnableJpaRepositories(basePackageClasses = SharedTestCachedEntityRepository.class)
public class SharedTestApplication {
}
