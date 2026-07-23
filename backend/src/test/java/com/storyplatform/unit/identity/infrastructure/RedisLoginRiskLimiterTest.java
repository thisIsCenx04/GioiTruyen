package com.storyplatform.unit.identity.infrastructure;

import com.storyplatform.identity.application.LoginRiskUnavailableException;
import com.storyplatform.identity.infrastructure.LoginRiskProperties;
import com.storyplatform.identity.infrastructure.security
        .RedisLoginRiskLimiter;
import com.storyplatform.shared.cache.RedisKeyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisLoginRiskLimiterTest {

    private StringRedisTemplate redis;
    private RedisLoginRiskLimiter limiter;

    @BeforeEach
    void configure() {
        redis = mock(StringRedisTemplate.class);
        limiter = new RedisLoginRiskLimiter(
                redis,
                new RedisKeyFactory("app", "v1", "test"),
                new LoginRiskProperties(
                        5,
                        20,
                        Duration.ofMinutes(15),
                        "unused"
                ),
                "0123456789abcdef0123456789abcdef"
                        .getBytes(StandardCharsets.UTF_8)
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void allowsAndDeniesUsingOpaqueKeysOnly() {
        when(redis.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenReturn(1L, 0L);

        assertThat(limiter.allow(
                "reader@example.com",
                "203.0.113.10"
        )).isTrue();
        assertThat(limiter.allow(
                "reader@example.com",
                "203.0.113.10"
        )).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisOutageFailsClosedForEveryMutation() {
        when(redis.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenThrow(new IllegalStateException("redis down"));

        assertThatThrownBy(() -> limiter.allow("email", "address"))
                .isInstanceOf(LoginRiskUnavailableException.class);
        assertThatThrownBy(() ->
                limiter.recordFailure("email", "address")
        ).isInstanceOf(LoginRiskUnavailableException.class);

        when(redis.delete(any(String.class)))
                .thenThrow(new IllegalStateException("redis down"));
        assertThatThrownBy(() ->
                limiter.recordSuccess("email", "address")
        ).isInstanceOf(LoginRiskUnavailableException.class);
    }

    @Test
    void rejectsWeakFingerprintKey() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new RedisLoginRiskLimiter(
                        redis,
                        new RedisKeyFactory("app", "v1", "test"),
                        new LoginRiskProperties(
                                5,
                                20,
                                Duration.ofMinutes(15),
                                "unused"
                        ),
                        new byte[31]
                )
        ).withMessage(
                "Login risk HMAC key must contain at least 32 bytes"
        );
    }
}
