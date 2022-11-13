package com.guflimc.brick.orm.data;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class PlatformConverter implements AttributeConverter<PlatformValue, String> {

    @Override
    public String convertToDatabaseColumn(PlatformValue attribute) {
        return attribute.value();
    }

    @Override
    public PlatformValue convertToEntityAttribute(String dbData) {
        return new PlatformValue(dbData);
    }

}
