package io.github.javaquasar.hazelcast.toolkit.spring.test.l2issue;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class SharedIssueUserGroupPkWithConverter implements Serializable {

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private SharedIssueUser user;

    @Column(name = "type_id", nullable = false)
    @Convert(converter = SharedIssueUserGroupTypeConverter.class)
    private SharedIssueUserGroupType type;

    protected SharedIssueUserGroupPkWithConverter() {
    }

    public SharedIssueUserGroupPkWithConverter(SharedIssueUser user, SharedIssueUserGroupType type) {
        this.user = user;
        this.type = type;
    }

    public SharedIssueUser getUser() {
        return user;
    }

    public SharedIssueUserGroupType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SharedIssueUserGroupPkWithConverter that)) {
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
