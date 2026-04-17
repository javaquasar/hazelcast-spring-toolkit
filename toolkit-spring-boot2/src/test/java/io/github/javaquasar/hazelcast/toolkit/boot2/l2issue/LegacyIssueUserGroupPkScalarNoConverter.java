package io.github.javaquasar.hazelcast.toolkit.boot2.l2issue;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class LegacyIssueUserGroupPkScalarNoConverter implements Serializable {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_code", nullable = false)
    private LegacyIssueUserGroupType type;

    protected LegacyIssueUserGroupPkScalarNoConverter() {
    }

    public LegacyIssueUserGroupPkScalarNoConverter(Long userId, LegacyIssueUserGroupType type) {
        this.userId = userId;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LegacyIssueUserGroupPkScalarNoConverter that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, type);
    }
}
