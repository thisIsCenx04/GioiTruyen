package com.storyplatform.shared.cache;

final class RedisValueSerializationException extends RuntimeException {

    RedisValueSerializationException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
