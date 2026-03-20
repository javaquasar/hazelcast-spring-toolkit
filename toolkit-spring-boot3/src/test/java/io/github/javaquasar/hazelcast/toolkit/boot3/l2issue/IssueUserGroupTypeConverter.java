package io.github.javaquasar.hazelcast.toolkit.boot3.l2issue;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.Serializable;

@Converter(autoApply = false)
public class IssueUserGroupTypeConverter implements AttributeConverter<IssueUserGroupType, Integer>, Serializable {

    @Override
    public Integer convertToDatabaseColumn(IssueUserGroupType attribute) {
        return attribute == null ? null : attribute.getTypeId();
    }

    @Override
    public IssueUserGroupType convertToEntityAttribute(Integer dbData) {
        return IssueUserGroupType.fromInt(dbData);
    }
}


