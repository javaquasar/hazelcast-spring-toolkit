package io.github.javaquasar.hazelcast.toolkit.spring.test.l2issue;

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
public class SharedIssueUserGroupScalarNoConverter {

    @EmbeddedId
    private SharedIssueUserGroupPkScalarNoConverter id;

    @Column(name = "label", nullable = false)
    private String label;

    protected SharedIssueUserGroupScalarNoConverter() {
    }

    public SharedIssueUserGroupScalarNoConverter(SharedIssueUserGroupPkScalarNoConverter id, String label) {
        this.id = id;
        this.label = label;
    }

    public SharedIssueUserGroupPkScalarNoConverter getId() {
        return id;
    }
}
