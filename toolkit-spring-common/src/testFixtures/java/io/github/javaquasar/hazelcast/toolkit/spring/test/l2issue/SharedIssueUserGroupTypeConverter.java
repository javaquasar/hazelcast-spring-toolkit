package io.github.javaquasar.hazelcast.toolkit.spring.test.l2issue;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.Serializable;

@Converter(autoApply = false)
public class SharedIssueUserGroupTypeConverter
        implements AttributeConverter<SharedIssueUserGroupType, Integer>, Serializable {

    @Override
    public Integer convertToDatabaseColumn(SharedIssueUserGroupType attribute) {
        return attribute == null ? null : attribute.getTypeId();
    }

    @Override
    public SharedIssueUserGroupType convertToEntityAttribute(Integer dbData) {
        return SharedIssueUserGroupType.fromInt(dbData);
    }
}
