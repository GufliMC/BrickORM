package com.guflimc.brick.orm.ebean.converters;

import javax.persistence.AttributeConverter;

public abstract class JakartaWrapperConverter<X, Y> implements AttributeConverter<X, Y> {

    private final jakarta.persistence.AttributeConverter<X, Y> jakartaConverter;

    public JakartaWrapperConverter(jakarta.persistence.AttributeConverter<X, Y> jakaratConverter) {
        this.jakartaConverter = jakaratConverter;
    }

    @Override
    public Y convertToDatabaseColumn(X attribute) {
        return jakartaConverter.convertToDatabaseColumn(attribute);
    }

    @Override
    public X convertToEntityAttribute(Y dbData) {
        return jakartaConverter.convertToEntityAttribute(dbData);
    }
}
