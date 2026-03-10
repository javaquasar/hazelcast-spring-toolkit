package io.github.javaquasar.hazelcast.toolkit.boot3.l2;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "test_cached_entities")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = TestCachedEntity.CACHE_REGION)
public class TestCachedEntity {

    public static final String CACHE_REGION = "test-entity-region";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    protected TestCachedEntity() {
    }

    public TestCachedEntity(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
