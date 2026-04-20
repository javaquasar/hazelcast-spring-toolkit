package io.github.javaquasar.hazelcast.toolkit.spring.test.l2issue;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class SharedIssueUserGroupPkScalarNoConverter implements Serializable {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_code", nullable = false)
    private SharedIssueUserGroupType type;

    protected SharedIssueUserGroupPkScalarNoConverter() {
    }

    public SharedIssueUserGroupPkScalarNoConverter(Long userId, SharedIssueUserGroupType type) {
        this.userId = userId;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SharedIssueUserGroupPkScalarNoConverter that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, type);
    }
}
