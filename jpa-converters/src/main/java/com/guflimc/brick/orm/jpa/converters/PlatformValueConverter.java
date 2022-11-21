package com.guflimc.brick.orm.jpa.converters;

import com.guflimc.brick.orm.api.data.PlatformValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class PlatformValueConverter implements AttributeConverter<PlatformValue, String>, javax.persistence.AttributeConverter<PlatformValue, String> {

    @Override
    public String convertToDatabaseColumn(PlatformValue attribute) {
        return attribute.serializedValue();
    }

    @Override
    public PlatformValue convertToEntityAttribute(String dbData) {
        return new PlatformValue(dbData);
    }

}
