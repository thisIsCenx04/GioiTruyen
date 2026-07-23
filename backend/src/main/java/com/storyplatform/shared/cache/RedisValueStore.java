package com.storyplatform.shared.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * Narrow string-only Redis port. Implementations must not use Java serialization.
 */
public interface RedisValueStore {

    Optional<String> get(RedisKey key);

    void put(RedisKey key, String value, Duration ttl);
}
