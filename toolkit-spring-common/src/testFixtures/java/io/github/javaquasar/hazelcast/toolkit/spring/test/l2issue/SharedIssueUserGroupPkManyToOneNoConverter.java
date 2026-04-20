package io.github.javaquasar.hazelcast.toolkit.spring.test.l2issue;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class SharedIssueUserGroupPkManyToOneNoConverter implements Serializable {

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private SharedIssueUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_code", nullable = false)
    private SharedIssueUserGroupType type;

    protected SharedIssueUserGroupPkManyToOneNoConverter() {
    }

    public SharedIssueUserGroupPkManyToOneNoConverter(SharedIssueUser user, SharedIssueUserGroupType type) {
        this.user = user;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SharedIssueUserGroupPkManyToOneNoConverter that)) {
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
