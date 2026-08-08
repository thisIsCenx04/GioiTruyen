package com.storyplatform.community.infrastructure;

import com.storyplatform.community.application.CommentException;
import com.storyplatform.community.application.CommentRateLimiter;
import com.storyplatform.shared.cache.RedisKeyFactory;
import com.storyplatform.shared.cache.RedisNamespace;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Redis-backed sliding-window comment rate limiter. The quota and window are
 * configurable. A Lua-based atomic increment is used to avoid race conditions.
 *
 * <p>If Redis is unavailable the limiter fails closed (throws
 * {@link CommentException} with kind {@code UNAVAILABLE}).
 */
public final class RedisCommentRateLimiter implements CommentRateLimiter {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_KEY_BYTES = 16;

    private static final RedisScript<Long> INCR_SCRIPT =
            RedisScript.of(
                    """
                    local current = redis.call('INCR', KEYS[1])
                    if current == 1 then
                        redis.call('PEXPIRE', KEYS[1], ARGV[1])
                    end
                    return current
                    """,
                    Long.class
            );

    private final StringRedisTemplate redis;
    private final RedisKeyFactory keyFactory;
    private final long quota;
    private final Duration window;
    private final byte[] hmacKey;

    public RedisCommentRateLimiter(
            StringRedisTemplate redis,
            RedisKeyFactory keyFactory,
            long quota,
            Duration window,
            byte[] hmacKey
    ) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.keyFactory = Objects.requireNonNull(keyFactory, "keyFactory");
        if (quota <= 0) {
            throw new IllegalArgumentException("quota must be positive");
        }
        this.quota = quota;
        this.window = Objects.requireNonNull(window, "window");
        Objects.requireNonNull(hmacKey, "hmacKey");
        if (hmacKey.length < MINIMUM_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "hmacKey must be at least " + MINIMUM_KEY_BYTES + " bytes"
            );
        }
        this.hmacKey = hmacKey.clone();
    }

    @Override
    public boolean allow(String userId) {
        String pseudonym = pseudonymize(userId);
        String key = keyFactory.create(RedisNamespace.RATE_LIMIT, pseudonym).value();
        try {
            Long count = redis.execute(
                    INCR_SCRIPT,
                    List.of(key),
                    String.valueOf(window.toMillis())
            );
            return count != null && count <= quota;
        } catch (RuntimeException exception) {
            throw new CommentException(
                    "COMMENT_RATE_LIMITER_UNAVAILABLE",
                    "Rate limiter temporarily unavailable",
                    CommentException.Kind.UNAVAILABLE
            );
        }
    }

    @Override
    public long retryAfterSeconds() {
        return window.toSeconds();
    }

    private String pseudonymize(String userId) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(hmacKey, ALGORITHM));
            return HexFormat.of().formatHex(
                    mac.doFinal(userId.getBytes())
            );
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HMAC unavailable", exception);
        }
    }
}
