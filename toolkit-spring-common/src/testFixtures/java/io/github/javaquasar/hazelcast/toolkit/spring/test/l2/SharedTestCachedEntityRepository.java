package io.github.javaquasar.hazelcast.toolkit.spring.test.l2;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SharedTestCachedEntityRepository extends JpaRepository<SharedTestCachedEntity, Long> {
}
