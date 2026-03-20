package io.github.javaquasar.hazelcast.toolkit.boot3.l2issue;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "l2_issue_group_with_converter")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "l2-issue-group-with-converter")
public class IssueUserGroupWithConverter {

    @EmbeddedId
    private IssueUserGroupPkWithConverter id;

    @Column(name = "label", nullable = false)
    private String label;

    protected IssueUserGroupWithConverter() {
    }

    public IssueUserGroupWithConverter(IssueUserGroupPkWithConverter id, String label) {
        this.id = id;
        this.label = label;
    }

    public IssueUserGroupPkWithConverter getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }
}



