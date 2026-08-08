package com.storyplatform.discovery.infrastructure.security;

import com.storyplatform.discovery.application
        .SuggestionRateLimitUnavailableException;
import com.storyplatform.discovery.application.port.SuggestionRateLimiter;
import com.storyplatform.shared.cache.RedisKey;
import com.storyplatform.shared.cache.RedisKeyFactory;
import com.storyplatform.shared.cache.RedisNamespace;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

public final class RedisSuggestionRateLimiter
        implements SuggestionRateLimiter {

    private static final String ALGORITHM = "HmacSHA256";
    private static final byte[] DOMAIN =
            "gioitruyen:suggestion-rate:v1"
                    .getBytes(StandardCharsets.UTF_8);
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT =
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
    private final SecretKeySpec fingerprintKey;

    public RedisSuggestionRateLimiter(
            StringRedisTemplate redis,
            RedisKeyFactory keys,
            int maximum,
            Duration window,
            byte[] rootKey
    ) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.keys = Objects.requireNonNull(keys, "keys");
        if (maximum < 1 || window == null || window.isZero()
                || window.isNegative()) {
            throw new IllegalArgumentException(
                    "suggestion rate limit configuration is invalid"
            );
        }
        this.maximum = maximum;
        this.window = window;
        fingerprintKey = new SecretKeySpec(derive(rootKey), ALGORITHM);
    }

    @Override
    public boolean allow(String subject) {
        try {
            RedisKey key = keys.create(
                    RedisNamespace.RATE_LIMIT,
                    "suggestions",
                    fingerprint(subject)
            );
            Long count = redis.execute(
                    INCREMENT_SCRIPT,
                    List.of(key.value()),
                    Long.toString(window.toMillis())
            );
            return count != null && count <= maximum;
        } catch (RuntimeException exception) {
            throw new SuggestionRateLimitUnavailableException(exception);
        }
    }

    @Override
    public long retryAfterSeconds() {
        return window.toSeconds();
    }

    private String fingerprint(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(fingerprintKey);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(
                            Objects.toString(value, "unknown")
                                    .getBytes(StandardCharsets.UTF_8)
                    ));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "cannot fingerprint suggestion subject",
                    exception
            );
        }
    }

    private static byte[] derive(byte[] rootKey) {
        if (rootKey == null || rootKey.length < 32) {
            throw new IllegalArgumentException(
                    "suggestion rate key requires at least 32 bytes"
            );
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(rootKey, ALGORITHM));
            return mac.doFinal(DOMAIN);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "cannot derive suggestion rate key",
                    exception
            );
        }
    }
}
