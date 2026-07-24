package com.storyplatform.unit.reading.infrastructure;

import com.storyplatform.reading.infrastructure.security
        .RedisReadingSessionQuota;
import com.storyplatform.shared.cache.RedisKeyFactory;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisReadingSessionQuotaTest {

    @Test
    void atomicallyBoundsStartsAndExposesRetryWindow() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), anyString()))
                .thenReturn(1L, 3L, null);
        var quota = new RedisReadingSessionQuota(
                redis,
                new RedisKeyFactory("app", "v1", "test"),
                2,
                Duration.ofMinutes(1)
        );

        assertThat(quota.allow("opaque")).isTrue();
        assertThat(quota.allow("opaque")).isFalse();
        assertThat(quota.allow("opaque")).isFalse();
        assertThat(quota.retryAfterSeconds()).isEqualTo(60);
    }

    @Test
    void validatesQuotaConfiguration() {
        assertThatThrownBy(() -> new RedisReadingSessionQuota(
                mock(StringRedisTemplate.class),
                new RedisKeyFactory("app", "v1", "test"),
                0,
                Duration.ofMinutes(1)
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
