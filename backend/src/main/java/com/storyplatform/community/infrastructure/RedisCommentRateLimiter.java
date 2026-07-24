package com.storyplatform.community.infrastructure;

import com.storyplatform.community.application.CommentException;
import com.storyplatform.community.application.CommentRateLimiter;
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

public final class RedisCommentRateLimiter implements CommentRateLimiter {

    private static final String ALGORITHM = "HmacSHA256";
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
    private final SecretKeySpec key;

    public RedisCommentRateLimiter(
            StringRedisTemplate redis,
            RedisKeyFactory keys,
            int maximum,
            Duration window,
            byte[] rootKey
    ) {
        this.redis = Objects.requireNonNull(redis);
        this.keys = Objects.requireNonNull(keys);
        if (maximum < 1 || window == null || window.isNegative()
                || window.isZero() || rootKey == null
                || rootKey.length < 32) {
            throw new IllegalArgumentException(
                    "comment rate limit configuration is invalid"
            );
        }
        this.maximum = maximum;
        this.window = window;
        this.key = new SecretKeySpec(rootKey, ALGORITHM);
    }

    @Override
    public boolean allow(String userId) {
        try {
            String subject = fingerprint(userId);
            String redisKey = keys.create(
                    RedisNamespace.RATE_LIMIT,
                    "comments",
                    subject
            ).value();
            Long count = redis.execute(
                    SCRIPT,
                    List.of(redisKey),
                    Long.toString(window.toMillis())
            );
            return count != null && count <= maximum;
        } catch (RuntimeException exception) {
            throw new CommentException(
                    "COMMENT_RATE_LIMIT_UNAVAILABLE",
                    "Comment protection is temporarily unavailable.",
                    CommentException.Kind.UNAVAILABLE
            );
        }
    }

    @Override
    public long retryAfterSeconds() {
        return Math.max(1, window.toSeconds());
    }

    private String fingerprint(String userId) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(userId.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
