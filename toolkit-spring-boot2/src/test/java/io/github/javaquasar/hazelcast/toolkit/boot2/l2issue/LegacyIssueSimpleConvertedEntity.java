package io.github.javaquasar.hazelcast.toolkit.boot2.l2issue;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "l2_issue_simple_converted")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "l2-issue-simple-converted")
public class LegacyIssueSimpleConvertedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = LegacyIssueUserGroupTypeConverter.class)
    @Column(name = "type_id", nullable = false)
    private LegacyIssueUserGroupType type;

    @Column(name = "label", nullable = false)
    private String label;

    protected LegacyIssueSimpleConvertedEntity() {
    }

    public LegacyIssueSimpleConvertedEntity(LegacyIssueUserGroupType type, String label) {
        this.type = type;
        this.label = label;
    }

    public Long getId() {
        return id;
    }

    public LegacyIssueUserGroupType getType() {
        return type;
    }
}
