package io.github.javaquasar.hazelcast.toolkit.example.boot3.repository;

import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExampleBookRepository extends JpaRepository<ExampleBookEntity, Long> {
}
