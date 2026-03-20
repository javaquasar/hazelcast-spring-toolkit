package io.github.javaquasar.hazelcast.toolkit.boot2.l2issue;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "legacy_l2_issue_group_m2o_no_converter")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "legacy-l2-issue-group-m2o-no-converter")
public class LegacyIssueUserGroupManyToOneNoConverter {

    @EmbeddedId
    private LegacyIssueUserGroupPkManyToOneNoConverter id;

    @Column(name = "label", nullable = false)
    private String label;

    protected LegacyIssueUserGroupManyToOneNoConverter() {
    }

    public LegacyIssueUserGroupManyToOneNoConverter(LegacyIssueUserGroupPkManyToOneNoConverter id, String label) {
        this.id = id;
        this.label = label;
    }
}


