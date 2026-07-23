package com.storyplatform.shared.cache;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

final class SpringRedisValueStore implements RedisValueStore {

    private final StringRedisTemplate redis;

    SpringRedisValueStore(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis");
    }

    @Override
    public Optional<String> get(RedisKey key) {
        return Optional.ofNullable(
                redis.opsForValue().get(key.value())
        );
    }

    @Override
    public void put(
            RedisKey key,
            String value,
            Duration ttl
    ) {
        redis.opsForValue().set(key.value(), value, ttl);
    }
}
