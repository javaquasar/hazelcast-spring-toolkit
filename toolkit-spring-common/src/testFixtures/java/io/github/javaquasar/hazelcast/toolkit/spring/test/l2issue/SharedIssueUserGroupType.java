package io.github.javaquasar.hazelcast.toolkit.spring.test.l2issue;

public enum SharedIssueUserGroupType {
    REGULAR(1),
    VIP(2);

    private final int typeId;

    SharedIssueUserGroupType(int typeId) {
        this.typeId = typeId;
    }

    public int getTypeId() {
        return typeId;
    }

    public static SharedIssueUserGroupType fromInt(Integer value) {
        if (value == null) {
            return null;
        }
        for (SharedIssueUserGroupType type : values()) {
            if (type.typeId == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown SharedIssueUserGroupType id: " + value);
    }
}
