package com.guflimc.brick.orm.data;

public final class PlatformType<T> {

    private final Class<T> type;

    private final Serializer<T> serializer;
    private final Deserializer<T> deserializer;

    public PlatformType(Class<T> type, Serializer<T> serializer, Deserializer<T> deserializer) {
        this.type = type;
        this.serializer = serializer;
        this.deserializer = deserializer;
    }

    Class<T> type() {
        return type;
    }

    String serialize(T value) {
        return serializer.serialize(value);
    }

    T deserialize(String value) {
        return deserializer.deserialize(value);
    }

    //

    @FunctionalInterface
    public interface Serializer<T> {
        String serialize(T value);
    }

    @FunctionalInterface
    public interface Deserializer<T> {
        T deserialize(String value);
    }
}
