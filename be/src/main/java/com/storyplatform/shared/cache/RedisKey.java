package com.storyplatform.shared.cache;

import java.util.Objects;

/**
 * A validated Redis key with its workload namespace retained as metadata.
 */
public record RedisKey(
        RedisNamespace namespace,
        String value
) {

    public RedisKey {
        Objects.requireNonNull(namespace, "namespace");
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Redis key must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
