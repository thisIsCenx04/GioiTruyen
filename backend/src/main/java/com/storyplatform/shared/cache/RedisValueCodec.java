package com.storyplatform.shared.cache;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * JSON codec used instead of unsafe native Java object serialization.
 */
public final class RedisValueCodec {

    private final ObjectMapper objectMapper;

    public RedisValueCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper"
        );
    }

    public String encode(Object value) {
        Objects.requireNonNull(value, "value");
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new RedisValueSerializationException(
                    "Redis value could not be encoded",
                    exception
            );
        }
    }

    public <T> T decode(
            String value,
            Class<T> type
    ) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(type, "type");
        try {
            return objectMapper.readValue(value, type);
        } catch (JacksonException exception) {
            throw new RedisValueSerializationException(
                    "Redis value could not be decoded",
                    exception
            );
        }
    }
}
