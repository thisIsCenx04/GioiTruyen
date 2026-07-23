package com.storyplatform.unit.shared.events.persistence;

import com.storyplatform.shared.events.persistence.OutboxWorkerProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxWorkerPropertiesTest {

    @Test
    void validPositiveDurationRangeIsAccepted() {
        assertThat(properties(
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofMinutes(15)
        ).isDurationPolicyValid()).isTrue();
    }

    @Test
    void nullZeroNegativeAndInvertedDurationsAreRejected() {
        assertThat(properties(
                null,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofMinutes(1)
        ).isDurationPolicyValid()).isFalse();
        assertThat(properties(
                Duration.ofSeconds(1),
                Duration.ZERO,
                Duration.ofSeconds(1),
                Duration.ofMinutes(1)
        ).isDurationPolicyValid()).isFalse();
        assertThat(properties(
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofSeconds(-1),
                Duration.ofMinutes(1)
        ).isDurationPolicyValid()).isFalse();
        assertThat(properties(
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofMinutes(2),
                Duration.ofMinutes(1)
        ).isDurationPolicyValid()).isFalse();
    }

    private static OutboxWorkerProperties properties(
            Duration lease,
            Duration poll,
            Duration initial,
            Duration maximum
    ) {
        return new OutboxWorkerProperties(
                false,
                25,
                lease,
                poll,
                8,
                initial,
                maximum
        );
    }
}
