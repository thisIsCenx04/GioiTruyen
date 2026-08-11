package com.storyplatform.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyplatform.auth.application.LoginAttemptLimiter;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {

    private static final String KEY = "reader@gioitruyen.com";

    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter();

    @Test
    void allowsAttemptsBelowTheThreshold() {
        Instant now = Instant.parse("2026-08-10T10:00:00Z");
        for (int attempt = 0; attempt < 4; attempt++) {
            assertThat(limiter.retryAfterSeconds(KEY, now)).isZero();
            limiter.recordFailure(KEY, now);
        }
        assertThat(limiter.retryAfterSeconds(KEY, now)).isZero();
    }

    @Test
    void blocksOnceTheThresholdIsReached() {
        Instant now = Instant.parse("2026-08-10T10:00:00Z");
        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.recordFailure(KEY, now);
        }
        assertThat(limiter.retryAfterSeconds(KEY, now)).isGreaterThan(0);
    }

    @Test
    void releasesTheBlockAfterTheLockoutExpires() {
        Instant now = Instant.parse("2026-08-10T10:00:00Z");
        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.recordFailure(KEY, now);
        }
        assertThat(limiter.retryAfterSeconds(KEY, now.plus(Duration.ofMinutes(16)))).isZero();
    }

    @Test
    void forgivesFailuresSpreadBeyondTheWindow() {
        Instant now = Instant.parse("2026-08-10T10:00:00Z");
        // Four failures, then a long gap: the streak restarts rather than
        // carrying over, so the fifth failure must not lock the account.
        for (int attempt = 0; attempt < 4; attempt++) {
            limiter.recordFailure(KEY, now);
        }
        Instant later = now.plus(Duration.ofMinutes(20));
        limiter.recordFailure(KEY, later);
        assertThat(limiter.retryAfterSeconds(KEY, later)).isZero();
    }

    @Test
    void successClearsTheStreak() {
        Instant now = Instant.parse("2026-08-10T10:00:00Z");
        for (int attempt = 0; attempt < 4; attempt++) {
            limiter.recordFailure(KEY, now);
        }
        limiter.recordSuccess(KEY);
        limiter.recordFailure(KEY, now);
        assertThat(limiter.retryAfterSeconds(KEY, now)).isZero();
    }

    @Test
    void tracksEachAccountSeparately() {
        Instant now = Instant.parse("2026-08-10T10:00:00Z");
        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.recordFailure(KEY, now);
        }
        assertThat(limiter.retryAfterSeconds("someone-else@gioitruyen.com", now)).isZero();
    }
}
