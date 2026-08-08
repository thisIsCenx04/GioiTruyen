package com.storyplatform.unit.shared.cache;

import com.storyplatform.shared.cache.RedisTtlPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisTtlPolicyTest {

    private static final Duration MINIMUM = Duration.ofSeconds(30);
    private static final Duration MAXIMUM = Duration.ofMinutes(60);

    @Test
    void appliesDeterministicPositiveAndNegativeJitter() {
        RedisTtlPolicy lower = policy(0.0);
        RedisTtlPolicy upper = policy(1.0);

        assertThat(lower.apply(Duration.ofSeconds(100)))
                .isEqualTo(Duration.ofSeconds(90));
        assertThat(upper.apply(Duration.ofSeconds(100)))
                .isEqualTo(Duration.ofSeconds(110));
    }

    @Test
    void clampsJitterToConfiguredTtlBounds() {
        assertThat(policy(0.0).apply(MINIMUM)).isEqualTo(MINIMUM);
        assertThat(policy(1.0).apply(MAXIMUM)).isEqualTo(MAXIMUM);
    }

    @Test
    void rejectsRequestedTtlOutsidePolicy() {
        RedisTtlPolicy policy = policy(0.5);

        assertThatThrownBy(() -> policy.apply(Duration.ofSeconds(29)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.apply(Duration.ofMinutes(61)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidConfigurationAndRandomSource() {
        assertThatThrownBy(() -> new RedisTtlPolicy(
                MAXIMUM,
                MINIMUM,
                0.1,
                () -> 0.5
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisTtlPolicy(
                MINIMUM,
                MAXIMUM,
                0.6,
                () -> 0.5
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisTtlPolicy(
                MINIMUM,
                MAXIMUM,
                0.1,
                () -> 1.1
        ).apply(Duration.ofMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static RedisTtlPolicy policy(double random) {
        return new RedisTtlPolicy(
                MINIMUM,
                MAXIMUM,
                0.1,
                () -> random
        );
    }
}
