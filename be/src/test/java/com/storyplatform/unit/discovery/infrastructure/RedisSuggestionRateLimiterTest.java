package com.storyplatform.unit.discovery.infrastructure;

import com.storyplatform.discovery.application
        .SuggestionRateLimitUnavailableException;
import com.storyplatform.discovery.infrastructure.security
        .RedisSuggestionRateLimiter;
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

class RedisSuggestionRateLimiterTest {

    @Test
    @SuppressWarnings("unchecked")
    void allowsThirtyRequestsThenDeniesWithOpaqueKeys() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenReturn(30L, 31L);
        var limiter = limiter(redis);

        assertThat(limiter.allow("203.0.113.10")).isTrue();
        assertThat(limiter.allow("203.0.113.10")).isFalse();
        assertThat(limiter.retryAfterSeconds()).isEqualTo(60);
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisFailureFailsClosedAndConfigurationIsValidated() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenThrow(new IllegalStateException("down"));

        assertThatThrownBy(() -> limiter(redis).allow("client"))
                .isInstanceOf(
                        SuggestionRateLimitUnavailableException.class
                );
        assertThatThrownBy(() -> new RedisSuggestionRateLimiter(
                redis,
                new RedisKeyFactory("app", "v1", "test"),
                0,
                Duration.ofMinutes(1),
                new byte[32]
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisSuggestionRateLimiter(
                redis,
                new RedisKeyFactory("app", "v1", "test"),
                30,
                Duration.ofMinutes(1),
                new byte[1]
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static RedisSuggestionRateLimiter limiter(
            StringRedisTemplate redis
    ) {
        return new RedisSuggestionRateLimiter(
                redis,
                new RedisKeyFactory("app", "v1", "test"),
                30,
                Duration.ofMinutes(1),
                new byte[32]
        );
    }
}
