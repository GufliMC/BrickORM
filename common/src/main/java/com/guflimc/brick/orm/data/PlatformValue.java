package com.guflimc.brick.orm.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlatformValue {

    private @Nullable String value;

    PlatformValue(@Nullable String value) {
        this.value = value;
    }

    String value() {
        return value;
    }

    //

    public <T> T get(@NotNull PlatformType<T> type) {
        if ( value == null ) {
            return null;
        }
        return type.deserialize(value);
    }

    public <T> void set(@NotNull PlatformType<T> type, @Nullable T value) {
        if ( value == null ) {
            this.value = null;
            return;
        }
        this.value = type.serialize(value);
    }
}
