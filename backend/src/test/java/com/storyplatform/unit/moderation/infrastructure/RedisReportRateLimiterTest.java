package com.storyplatform.unit.moderation.infrastructure;

import com.storyplatform.moderation.application
        .CommunityReportException;
import com.storyplatform.moderation.infrastructure
        .RedisReportRateLimiter;
import com.storyplatform.shared.cache.RedisKeyFactory;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisReportRateLimiterTest {

    @Test
    @SuppressWarnings("unchecked")
    void adjustsQuotaByTrustAndFailsClosed() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenReturn(5L, 6L)
                .thenThrow(new IllegalStateException("down"));
        RedisReportRateLimiter limiter = limiter(redis);

        assertThat(limiter.allow("reporter", 0)).isTrue();
        assertThat(limiter.allow("reporter", 0)).isFalse();
        assertThatThrownBy(() -> limiter.allow("reporter", 100))
                .isInstanceOfSatisfying(
                        CommunityReportException.class,
                        error -> assertThat(error.kind()).isEqualTo(
                                CommunityReportException.Kind.UNAVAILABLE
                        )
                );
        assertThat(limiter.retryAfterSeconds()).isEqualTo(86_400);
    }

    @Test
    void validatesWindowAndKeyMaterial() {
        assertThatThrownBy(() -> new RedisReportRateLimiter(
                mock(StringRedisTemplate.class),
                new RedisKeyFactory("app", "v1", "test"),
                Duration.ofDays(2),
                new byte[32]
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisReportRateLimiter(
                mock(StringRedisTemplate.class),
                new RedisKeyFactory("app", "v1", "test"),
                Duration.ofDays(1),
                new byte[1]
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static RedisReportRateLimiter limiter(
            StringRedisTemplate redis
    ) {
        return new RedisReportRateLimiter(
                redis,
                new RedisKeyFactory("app", "v1", "test"),
                Duration.ofDays(1),
                new byte[32]
        );
    }
}
