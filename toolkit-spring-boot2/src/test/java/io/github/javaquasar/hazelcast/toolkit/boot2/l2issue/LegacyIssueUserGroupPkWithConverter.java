package io.github.javaquasar.hazelcast.toolkit.boot2.l2issue;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class LegacyIssueUserGroupPkWithConverter implements Serializable {

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private LegacyIssueUser user;

    @Column(name = "type_id", nullable = false)
    @Convert(converter = LegacyIssueUserGroupTypeConverter.class)
    private LegacyIssueUserGroupType type;

    protected LegacyIssueUserGroupPkWithConverter() {
    }

    public LegacyIssueUserGroupPkWithConverter(LegacyIssueUser user, LegacyIssueUserGroupType type) {
        this.user = user;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LegacyIssueUserGroupPkWithConverter that)) {
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


