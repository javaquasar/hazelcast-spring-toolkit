package io.github.javaquasar.hazelcast.toolkit.boot2.l2issue;

public enum LegacyIssueUserGroupType {
    REGULAR(1),
    VIP(2);

    private final int typeId;

    LegacyIssueUserGroupType(int typeId) {
        this.typeId = typeId;
    }

    public int getTypeId() {
        return typeId;
    }

    public static LegacyIssueUserGroupType fromInt(Integer value) {
        if (value == null) {
            return null;
        }
        for (LegacyIssueUserGroupType type : values()) {
            if (type.typeId == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown LegacyIssueUserGroupType id: " + value);
    }
}


