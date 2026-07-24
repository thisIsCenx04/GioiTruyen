package com.storyplatform.moderation.infrastructure;

import com.storyplatform.moderation.application
        .CommunityReportException;
import com.storyplatform.moderation.application.ReportRateLimiter;
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

public final class RedisReportRateLimiter
        implements ReportRateLimiter {

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
    private final Duration window;
    private final SecretKeySpec key;

    public RedisReportRateLimiter(
            StringRedisTemplate redis,
            RedisKeyFactory keys,
            Duration window,
            byte[] rootKey
    ) {
        this.redis = Objects.requireNonNull(redis);
        this.keys = Objects.requireNonNull(keys);
        if (window == null || window.isZero() || window.isNegative()
                || window.compareTo(Duration.ofDays(1)) > 0
                || rootKey == null || rootKey.length < 32) {
            throw new IllegalArgumentException(
                    "report rate limit configuration is invalid"
            );
        }
        this.window = window;
        this.key = new SecretKeySpec(rootKey, ALGORITHM);
    }

    @Override
    public boolean allow(String reporterId, int trustScore) {
        int maximum = 5 + Math.clamp(trustScore, 0, 100) / 20;
        try {
            String redisKey = keys.create(
                    RedisNamespace.RATE_LIMIT,
                    "reports",
                    fingerprint(reporterId)
            ).value();
            Long count = redis.execute(
                    SCRIPT,
                    List.of(redisKey),
                    Long.toString(window.toMillis())
            );
            return count != null && count <= maximum;
        } catch (RuntimeException exception) {
            throw new CommunityReportException(
                    "REPORT_RATE_LIMIT_UNAVAILABLE",
                    "Report protection is temporarily unavailable.",
                    CommunityReportException.Kind.UNAVAILABLE
            );
        }
    }

    @Override
    public long retryAfterSeconds() {
        return Math.max(1, window.toSeconds());
    }

    private String fingerprint(String reporterId) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(
                            reporterId.getBytes(StandardCharsets.UTF_8)
                    ));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
