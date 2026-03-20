package io.github.javaquasar.hazelcast.toolkit.boot3.l2issue;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "l2_issue_simple_converted")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "l2-issue-simple-converted")
public class IssueSimpleConvertedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = IssueUserGroupTypeConverter.class)
    @Column(name = "type_id", nullable = false)
    private IssueUserGroupType type;

    @Column(name = "label", nullable = false)
    private String label;

    protected IssueSimpleConvertedEntity() {
    }

    public IssueSimpleConvertedEntity(IssueUserGroupType type, String label) {
        this.type = type;
        this.label = label;
    }

    public Long getId() {
        return id;
    }

    public IssueUserGroupType getType() {
        return type;
    }
}


