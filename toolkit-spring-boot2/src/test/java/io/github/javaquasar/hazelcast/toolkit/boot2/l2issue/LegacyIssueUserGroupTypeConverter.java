package io.github.javaquasar.hazelcast.toolkit.boot2.l2issue;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.io.Serializable;

@Converter(autoApply = false)
public class LegacyIssueUserGroupTypeConverter implements AttributeConverter<LegacyIssueUserGroupType, Integer>, Serializable {

    @Override
    public Integer convertToDatabaseColumn(LegacyIssueUserGroupType attribute) {
        return attribute == null ? null : attribute.getTypeId();
    }

    @Override
    public LegacyIssueUserGroupType convertToEntityAttribute(Integer dbData) {
        return LegacyIssueUserGroupType.fromInt(dbData);
    }
}


