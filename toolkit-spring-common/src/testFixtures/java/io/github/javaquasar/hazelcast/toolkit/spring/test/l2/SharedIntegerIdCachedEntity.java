package io.github.javaquasar.hazelcast.toolkit.spring.test.l2;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@javax.persistence.Entity
@jakarta.persistence.Entity
@javax.persistence.Table(name = "test_integer_id_cached_entities")
@jakarta.persistence.Table(name = "test_integer_id_cached_entities")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = SharedIntegerIdCachedEntity.CACHE_REGION)
public class SharedIntegerIdCachedEntity {

    public static final String CACHE_REGION = "test-integer-id-entity-region";

    @javax.persistence.Id
    @jakarta.persistence.Id
    private Integer id;

    @javax.persistence.Column(nullable = false)
    @jakarta.persistence.Column(nullable = false)
    private String name;

    protected SharedIntegerIdCachedEntity() {
    }

    public SharedIntegerIdCachedEntity(Integer id, String name) {
        this.id = id;
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
