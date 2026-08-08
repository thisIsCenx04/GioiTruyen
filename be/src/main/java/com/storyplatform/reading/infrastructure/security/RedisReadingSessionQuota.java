package com.storyplatform.reading.infrastructure.security;

import com.storyplatform.reading.application.port.ReadingSessionQuota;
import com.storyplatform.shared.cache.RedisKey;
import com.storyplatform.shared.cache.RedisKeyFactory;
import com.storyplatform.shared.cache.RedisNamespace;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class RedisReadingSessionQuota
        implements ReadingSessionQuota {

    private static final DefaultRedisScript<Long> SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local count = redis.call('INCR', KEYS[1])
                    if count == 1 then
                      redis.call('PEXPIRE', KEYS[1], ARGV[1])
                    end
                    return count
                    """,
                    Long.class
            );
    private final StringRedisTemplate redis;
    private final RedisKeyFactory keys;
    private final int maximum;
    private final Duration window;

    public RedisReadingSessionQuota(
            StringRedisTemplate redis,
            RedisKeyFactory keys,
            int maximum,
            Duration window
    ) {
        this.redis = Objects.requireNonNull(redis);
        this.keys = Objects.requireNonNull(keys);
        if (maximum < 1 || window == null || window.isZero()
                || window.isNegative()) {
            throw new IllegalArgumentException(
                    "reading session quota configuration is invalid"
            );
        }
        this.maximum = maximum;
        this.window = window;
    }

    @Override
    public boolean allow(String actorRef) {
        RedisKey key = keys.create(
                RedisNamespace.RATE_LIMIT,
                "reading-session-start",
                actorRef
        );
        Long count = redis.execute(
                SCRIPT,
                List.of(key.value()),
                Long.toString(window.toMillis())
        );
        return count != null && count <= maximum;
    }

    @Override
    public long retryAfterSeconds() {
        return Math.max(1, window.toSeconds());
    }
}
