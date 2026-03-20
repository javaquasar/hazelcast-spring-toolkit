package io.github.javaquasar.hazelcast.toolkit.boot2.l2issue;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class LegacyIssueUserGroupPkManyToOneNoConverter implements Serializable {

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private LegacyIssueUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_code", nullable = false)
    private LegacyIssueUserGroupType type;

    protected LegacyIssueUserGroupPkManyToOneNoConverter() {
    }

    public LegacyIssueUserGroupPkManyToOneNoConverter(LegacyIssueUser user, LegacyIssueUserGroupType type) {
        this.user = user;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LegacyIssueUserGroupPkManyToOneNoConverter that)) {
            return false;
        }
        return Objects.equals(user == null ? null : user.getId(), that.user == null ? null : that.user.getId())
                && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(user == null ? null : user.getId(), type);
    }
}


