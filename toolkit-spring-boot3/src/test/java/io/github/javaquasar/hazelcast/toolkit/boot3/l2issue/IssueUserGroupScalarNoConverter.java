package io.github.javaquasar.hazelcast.toolkit.boot3.l2issue;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "l2_issue_group_scalar_no_converter")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "l2-issue-group-scalar-no-converter")
public class IssueUserGroupScalarNoConverter {

    @EmbeddedId
    private IssueUserGroupPkScalarNoConverter id;

    @Column(name = "label", nullable = false)
    private String label;

    protected IssueUserGroupScalarNoConverter() {
    }

    public IssueUserGroupScalarNoConverter(IssueUserGroupPkScalarNoConverter id, String label) {
        this.id = id;
        this.label = label;
    }

    public IssueUserGroupPkScalarNoConverter getId() {
        return id;
    }
}



