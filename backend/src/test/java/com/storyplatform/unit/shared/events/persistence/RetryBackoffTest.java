package com.storyplatform.unit.shared.events.persistence;

import com.storyplatform.shared.events.persistence.RetryBackoff;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RetryBackoffTest {

    private final RetryBackoff backoff = new RetryBackoff(
            Duration.ofSeconds(1),
            Duration.ofSeconds(30)
    );

    @Test
    void doublesDelayUntilConfiguredMaximum() {
        assertThat(backoff.delayForAttempt(1)).isEqualTo(
                Duration.ofSeconds(1)
        );
        assertThat(backoff.delayForAttempt(2)).isEqualTo(
                Duration.ofSeconds(2)
        );
        assertThat(backoff.delayForAttempt(5)).isEqualTo(
                Duration.ofSeconds(16)
        );
        assertThat(backoff.delayForAttempt(6)).isEqualTo(
                Duration.ofSeconds(30)
        );
        assertThat(backoff.delayForAttempt(100)).isEqualTo(
                Duration.ofSeconds(30)
        );
    }

    @Test
    void nonPositiveAttemptIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                backoff.delayForAttempt(0)
        ).withMessage("attempt must be positive");
    }
}
