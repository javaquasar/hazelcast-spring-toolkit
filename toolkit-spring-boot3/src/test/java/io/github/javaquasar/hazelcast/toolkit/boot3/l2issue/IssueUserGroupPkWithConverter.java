package io.github.javaquasar.hazelcast.toolkit.boot3.l2issue;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class IssueUserGroupPkWithConverter implements Serializable {

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private IssueUser user;

    @Column(name = "type_id", nullable = false)
    @Convert(converter = IssueUserGroupTypeConverter.class)
    private IssueUserGroupType type;

    protected IssueUserGroupPkWithConverter() {
    }

    public IssueUserGroupPkWithConverter(IssueUser user, IssueUserGroupType type) {
        this.user = user;
        this.type = type;
    }

    public IssueUser getUser() {
        return user;
    }

    public IssueUserGroupType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IssueUserGroupPkWithConverter that)) {
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



