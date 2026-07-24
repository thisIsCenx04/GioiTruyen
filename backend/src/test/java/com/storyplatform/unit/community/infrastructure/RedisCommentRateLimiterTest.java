package com.storyplatform.unit.community.infrastructure;

import com.storyplatform.community.application.CommentException;
import com.storyplatform.community.infrastructure.RedisCommentRateLimiter;
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

class RedisCommentRateLimiterTest {

    @Test
    @SuppressWarnings("unchecked")
    void allowsWithinQuotaAndFailsClosedWhenRedisIsUnavailable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenReturn(10L, 11L)
                .thenThrow(new IllegalStateException("down"));
        RedisCommentRateLimiter limiter = limiter(redis);

        assertThat(limiter.allow("private-user-id")).isTrue();
        assertThat(limiter.allow("private-user-id")).isFalse();
        assertThatThrownBy(() -> limiter.allow("private-user-id"))
                .isInstanceOfSatisfying(
                        CommentException.class,
                        error -> assertThat(error.kind()).isEqualTo(
                                CommentException.Kind.UNAVAILABLE
                        )
                );
        assertThat(limiter.retryAfterSeconds()).isEqualTo(60);
    }

    @Test
    void validatesSecuritySensitiveConfiguration() {
        assertThatThrownBy(() -> new RedisCommentRateLimiter(
                mock(StringRedisTemplate.class),
                new RedisKeyFactory("app", "v1", "test"),
                0,
                Duration.ofMinutes(1),
                new byte[32]
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisCommentRateLimiter(
                mock(StringRedisTemplate.class),
                new RedisKeyFactory("app", "v1", "test"),
                10,
                Duration.ofMinutes(1),
                new byte[2]
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static RedisCommentRateLimiter limiter(
            StringRedisTemplate redis
    ) {
        return new RedisCommentRateLimiter(
                redis,
                new RedisKeyFactory("app", "v1", "test"),
                10,
                Duration.ofMinutes(1),
                new byte[32]
        );
    }
}
