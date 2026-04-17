package io.github.javaquasar.hazelcast.toolkit.boot2.l2issue;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "l2_issue_group_with_converter")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "l2-issue-group-with-converter")
public class LegacyIssueUserGroupWithConverter {

    @EmbeddedId
    private LegacyIssueUserGroupPkWithConverter id;

    @Column(name = "label", nullable = false)
    private String label;

    protected LegacyIssueUserGroupWithConverter() {
    }

    public LegacyIssueUserGroupWithConverter(LegacyIssueUserGroupPkWithConverter id, String label) {
        this.id = id;
        this.label = label;
    }

    public LegacyIssueUserGroupPkWithConverter getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }
}
