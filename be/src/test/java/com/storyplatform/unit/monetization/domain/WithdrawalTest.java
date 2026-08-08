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

    @Test
    void reviewTransitionsAreTerminalAndRequireReleaseOnReject() {
        Withdrawal pending = withdrawal(100_000);
        Withdrawal approved = pending.approved(
                "70000000-0000-4000-8000-000000000001",
                "Verified finance approval.",
                "STANDARD",
                "withdrawal-risk-2026.1",
                "c".repeat(64),
                "d".repeat(64),
                NOW.plusSeconds(1)
        );
        Withdrawal rejected = pending.rejected(
                "70000000-0000-4000-8000-000000000001",
                "Verified finance rejection.",
                "STANDARD",
                "withdrawal-risk-2026.1",
                "e".repeat(64),
                "f".repeat(64),
                "80000000-0000-4000-8000-000000000001",
                NOW.plusSeconds(1)
        );

        assertThat(approved.state()).isEqualTo(Withdrawal.State.APPROVED);
        assertThat(rejected.state()).isEqualTo(Withdrawal.State.REJECTED);
        assertThatThrownBy(() -> approved.approved(
                "70000000-0000-4000-8000-000000000001",
                "Cannot review twice.",
                "STANDARD",
                "withdrawal-risk-2026.1",
                "c".repeat(64),
                "d".repeat(64),
                NOW.plusSeconds(2)
        )).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pending.approved(
                "70000000-0000-4000-8000-000000000001",
                "Review timestamp cannot be null.",
                "STANDARD",
                "withdrawal-risk-2026.1",
                "c".repeat(64),
                "d".repeat(64),
                null
        )).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pending.approved(
                "70000000-0000-4000-8000-000000000001",
                "Review timestamp cannot precede request.",
                "STANDARD",
                "withdrawal-risk-2026.1",
                "c".repeat(64),
                "d".repeat(64),
                NOW.minusSeconds(1)
        )).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pending.rejected(
                "70000000-0000-4000-8000-000000000001",
                "Rejection must reference its release.",
                "STANDARD",
                "withdrawal-risk-2026.1",
                "e".repeat(64),
                "f".repeat(64),
                null,
                NOW.plusSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void approvedWithdrawalProcessesThenReachesOnePayoutOutcome() {
        Withdrawal approved = withdrawal(100_000).approved(
                "70000000-0000-4000-8000-000000000001",
                "Verified finance approval.",
                "STANDARD",
                "withdrawal-risk-2026.1",
                "c".repeat(64),
                "d".repeat(64),
                NOW.plusSeconds(1)
        );
        Withdrawal processing = approved.processing();

        assertThat(processing.paid().state())
                .isEqualTo(Withdrawal.State.PAID);
        assertThat(processing.failed(
                "80000000-0000-4000-8000-000000000001"
        ).state()).isEqualTo(Withdrawal.State.FAILED);
        assertThatThrownBy(withdrawal(100_000)::processing)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(approved::paid)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> approved.failed(
                "80000000-0000-4000-8000-000000000001"
        )).isInstanceOf(IllegalStateException.class);
    }

    private static Withdrawal withdrawal(long gross) {
        return new Withdrawal(
                "10000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                "30000000-0000-4000-8000-000000000001",
                gross,
                20_000,
                gross - 20_000,
                "withdrawal-fee-2026.1",
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
                NOW,
                null, null, null, null, null, null, null, null
        );
    }
}
