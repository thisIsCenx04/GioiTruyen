package com.storyplatform.unit.monetization.domain;

import com.storyplatform.monetization.domain.Withdrawal;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WithdrawalTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T01:00:00Z");

    @Test
    void snapshotsVerifiedDestinationAndGrossReservationEvidence() {
        Withdrawal value = withdrawal(100_000);

        assertThat(value.destination().maskedLabel())
                .isEqualTo("VCB •••• 1234");
        assertThat(value.state())
                .isEqualTo(Withdrawal.State.PENDING_REVIEW);
    }

    @Test
    void rejectsOutOfRangeGrossAndInconsistentEvidence() {
        assertThatThrownBy(() -> withdrawal(99_999))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withdrawal(1_000_000_001))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Withdrawal.DestinationSnapshot(
                "30000000-0000-4000-8000-000000000001",
                "",
                "",
                0,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static Withdrawal withdrawal(long gross) {
        return new Withdrawal(
                "10000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                "30000000-0000-4000-8000-000000000001",
                gross,
                new Withdrawal.DestinationSnapshot(
                        "40000000-0000-4000-8000-000000000001",
                        "VCB •••• 1234",
                        "encrypted:v1:ciphertext",
                        1,
                        NOW.minusSeconds(60)
                ),
                Withdrawal.State.PENDING_REVIEW,
                "50000000-0000-4000-8000-000000000001",
                "60000000-0000-4000-8000-000000000001",
                "a".repeat(64),
                "b".repeat(64),
                NOW
        );
    }
}
