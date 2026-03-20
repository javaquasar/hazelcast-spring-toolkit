package io.github.javaquasar.hazelcast.toolkit.boot2.l2issue;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class LegacyIssueUserGroupPkScalarNoConverter implements Serializable {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "type_name", nullable = false)
    private String typeName;

    protected LegacyIssueUserGroupPkScalarNoConverter() {
    }

    public LegacyIssueUserGroupPkScalarNoConverter(Long userId, LegacyIssueUserGroupType type) {
        this.userId = userId;
        this.typeName = type.name();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LegacyIssueUserGroupPkScalarNoConverter that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(typeName, that.typeName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, typeName);
    }
}


