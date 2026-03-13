package io.github.javaquasar.hazelcast.toolkit.spring.test.l2;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@javax.persistence.Entity
@jakarta.persistence.Entity
@javax.persistence.Table(name = "test_cached_entities")
@jakarta.persistence.Table(name = "test_cached_entities")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = SharedTestCachedEntity.CACHE_REGION)
public class SharedTestCachedEntity {

    public static final String CACHE_REGION = "test-entity-region";

    @javax.persistence.Id
    @jakarta.persistence.Id
    @javax.persistence.GeneratedValue(strategy = javax.persistence.GenerationType.IDENTITY)
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;

    @javax.persistence.Column(nullable = false)
    @jakarta.persistence.Column(nullable = false)
    private String name;

    protected SharedTestCachedEntity() {
    }

    public SharedTestCachedEntity(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
