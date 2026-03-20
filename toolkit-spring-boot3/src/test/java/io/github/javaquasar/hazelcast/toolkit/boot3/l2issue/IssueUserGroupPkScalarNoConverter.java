package io.github.javaquasar.hazelcast.toolkit.boot3.l2issue;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class IssueUserGroupPkScalarNoConverter implements Serializable {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_code", nullable = false)
    private IssueUserGroupType type;

    protected IssueUserGroupPkScalarNoConverter() {
    }

    public IssueUserGroupPkScalarNoConverter(Long userId, IssueUserGroupType type) {
        this.userId = userId;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IssueUserGroupPkScalarNoConverter that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, type);
    }
}


