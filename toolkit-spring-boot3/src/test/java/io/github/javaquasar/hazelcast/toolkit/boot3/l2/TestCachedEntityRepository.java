package io.github.javaquasar.hazelcast.toolkit.boot3.l2;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TestCachedEntityRepository extends JpaRepository<TestCachedEntity, Long> {
}
