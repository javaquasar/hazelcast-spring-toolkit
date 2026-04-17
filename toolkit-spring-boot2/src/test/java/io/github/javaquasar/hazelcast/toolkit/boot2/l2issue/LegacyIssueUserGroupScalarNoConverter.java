package io.github.javaquasar.hazelcast.toolkit.boot2.l2issue;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "l2_issue_group_scalar_no_converter")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "l2-issue-group-scalar-no-converter")
public class LegacyIssueUserGroupScalarNoConverter {

    @EmbeddedId
    private LegacyIssueUserGroupPkScalarNoConverter id;

    @Column(name = "label", nullable = false)
    private String label;

    protected LegacyIssueUserGroupScalarNoConverter() {
    }

    public LegacyIssueUserGroupScalarNoConverter(LegacyIssueUserGroupPkScalarNoConverter id, String label) {
        this.id = id;
        this.label = label;
    }

    public LegacyIssueUserGroupPkScalarNoConverter getId() {
        return id;
    }
}
