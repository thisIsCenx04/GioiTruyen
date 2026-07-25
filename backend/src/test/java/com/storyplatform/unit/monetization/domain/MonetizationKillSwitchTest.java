package com.storyplatform.unit.monetization.domain;

import com.storyplatform.monetization.domain.MonetizationKillSwitch;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonetizationKillSwitchTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T01:00:00Z");

    @Test
    void acceptsPersistedAndSystemDefaultStates() {
        assertThat(state(
                MonetizationKillSwitch.Operation.TOPUP_CREDIT,
                1,
                "10000000-0000-4000-8000-000000000001",
                NOW
        ).version()).isOne();
        assertThat(state(
                MonetizationKillSwitch.Operation.TOPUP_CREDIT,
                0,
                "system-default",
                Instant.EPOCH
        ).changedBy()).isEqualTo("system-default");
    }

    @Test
    void rejectsMissingInvalidAndUntrustedStateFields() {
        List<org.assertj.core.api.ThrowableAssert.ThrowingCallable> invalid =
                List.of(
                        () -> state(null, 1,
                                "10000000-0000-4000-8000-000000000001",
                                NOW),
                        () -> state(
                                MonetizationKillSwitch.Operation.TOPUP_CREDIT,
                                -1,
                                "10000000-0000-4000-8000-000000000001",
                                NOW),
                        () -> state(
                                MonetizationKillSwitch.Operation.TOPUP_CREDIT,
                                1, null, NOW),
                        () -> state(
                                MonetizationKillSwitch.Operation.TOPUP_CREDIT,
                                1, "not-a-uuid", NOW),
                        () -> state(
                                MonetizationKillSwitch.Operation.TOPUP_CREDIT,
                                1,
                                "10000000-0000-4000-8000-000000000001",
                                null)
                );

        invalid.forEach(value -> assertThatThrownBy(value)
                .isInstanceOf(IllegalArgumentException.class));
    }

    private static MonetizationKillSwitch state(
            MonetizationKillSwitch.Operation operation,
            long version,
            String changedBy,
            Instant changedAt
    ) {
        return new MonetizationKillSwitch(
                operation,
                true,
                version,
                changedBy,
                changedAt
        );
    }
}
