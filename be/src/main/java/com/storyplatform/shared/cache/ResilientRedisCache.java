package com.storyplatform.shared.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Read-through cache that preserves source-of-truth reads during Redis outages.
 */
public final class ResilientRedisCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            ResilientRedisCache.class
    );

    private final RedisValueStore store;
    private final RedisValueCodec codec;
    private final RedisTtlPolicy ttlPolicy;
    private final RedisCacheTelemetry telemetry;

    public ResilientRedisCache(
            RedisValueStore store,
            RedisValueCodec codec,
            RedisTtlPolicy ttlPolicy,
            RedisCacheTelemetry telemetry
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.ttlPolicy = Objects.requireNonNull(ttlPolicy, "ttlPolicy");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
    }

    public <T> T getOrLoad(
            RedisKey key,
            Class<T> type,
            Duration ttl,
            Supplier<T> authoritativeLoader
    ) {
        requireCacheNamespace(key);
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(authoritativeLoader, "authoritativeLoader");
        Duration effectiveTtl = ttlPolicy.apply(ttl);

        Optional<T> cached = readCache(key, type);
        if (cached.isPresent()) {
            return cached.get();
        }

        T loaded = Objects.requireNonNull(
                authoritativeLoader.get(),
                "authoritativeLoader result"
        );
        writeCache(key, loaded, effectiveTtl);
        return loaded;
    }

    private <T> Optional<T> readCache(
            RedisKey key,
            Class<T> type
    ) {
        try {
            Optional<String> value = store.get(key);
            if (value.isEmpty()) {
                telemetry.record(key.namespace(), "read", "miss");
                return Optional.empty();
            }
            T decoded = codec.decode(value.orElseThrow(), type);
            telemetry.record(key.namespace(), "read", "hit");
            return Optional.of(decoded);
        } catch (RuntimeException exception) {
            telemetry.record(key.namespace(), "read", "degraded");
            logDegraded("read", exception);
            return Optional.empty();
        }
    }

    private void writeCache(
            RedisKey key,
            Object value,
            Duration ttl
    ) {
        try {
            store.put(key, codec.encode(value), ttl);
            telemetry.record(key.namespace(), "write", "stored");
        } catch (RuntimeException exception) {
            telemetry.record(key.namespace(), "write", "degraded");
            logDegraded("write", exception);
        }
    }

    private static void requireCacheNamespace(RedisKey key) {
        Objects.requireNonNull(key, "key");
        if (key.namespace().failureMode()
                != RedisFailureMode.FALL_BACK_TO_SOURCE) {
            throw new IllegalArgumentException(
                    "Only disposable cache keys may use source fallback"
            );
        }
    }

    private static void logDegraded(
            String operation,
            RuntimeException exception
    ) {
        LOGGER.atWarn()
                .addKeyValue("redis.namespace", "cache")
                .addKeyValue("redis.operation", operation)
                .addKeyValue(
                        "error.type",
                        exception.getClass().getSimpleName()
                )
                .log("Redis cache degraded to source-of-truth access");
    }
}
