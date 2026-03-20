package io.github.javaquasar.hazelcast.toolkit.boot3.l2issue;

public enum IssueUserGroupType {
    REGULAR(1),
    VIP(2);

    private final int typeId;

    IssueUserGroupType(int typeId) {
        this.typeId = typeId;
    }

    public int getTypeId() {
        return typeId;
    }

    public static IssueUserGroupType fromInt(Integer value) {
        if (value == null) {
            return null;
        }
        for (IssueUserGroupType type : values()) {
            if (type.typeId == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown IssueUserGroupType id: " + value);
    }
}


