package com.gufli.brick.orm.jpa.converters;

import com.gufli.brick.orm.api.data.SerializableValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class SerializableValueConverter implements AttributeConverter<SerializableValue, String> {

    @Override
    public String convertToDatabaseColumn(SerializableValue attribute) {
        return attribute.serializedValue();
    }

    @Override
    public SerializableValue convertToEntityAttribute(String dbData) {
        return new SerializableValue(dbData);
    }

}
