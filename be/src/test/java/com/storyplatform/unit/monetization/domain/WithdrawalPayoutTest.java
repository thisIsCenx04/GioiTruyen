package com.storyplatform.unit.monetization.domain;

import com.storyplatform.monetization.domain.WithdrawalPayout;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WithdrawalPayoutTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T01:00:00Z");

    @Test
    void processingCanRetryThenReachOneTerminalOutcome() {
        WithdrawalPayout processing = processing();
        WithdrawalPayout retry = processing.retry(
                "provider-001", "TIMEOUT", NOW.plusSeconds(30)
        );
        WithdrawalPayout paid = processing.paid(
                "provider-001",
                "20000000-0000-4000-8000-000000000001",
                NOW.plusSeconds(1)
        );
        WithdrawalPayout failed = processing.failed(
                "provider-001",
                "ACCOUNT_REJECTED",
                "30000000-0000-4000-8000-000000000001",
                NOW.plusSeconds(1)
        );

        assertThat(retry.nextAttemptAt()).isAfter(NOW);
        assertThat(paid.state()).isEqualTo(WithdrawalPayout.State.PAID);
        assertThat(failed.state()).isEqualTo(WithdrawalPayout.State.FAILED);
        assertThatThrownBy(() -> paid.retry(
                null, "TIMEOUT", NOW.plusSeconds(30)
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnsafeProviderAndInconsistentTerminalEvidence() {
        assertThatThrownBy(() -> new WithdrawalPayout(
                "invalid",
                "BAD",
                "short",
                WithdrawalPayout.State.PAID,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> processing().paid(
                null,
                "20000000-0000-4000-8000-000000000001",
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> processing().failed(
                "provider-001",
                null,
                "30000000-0000-4000-8000-000000000001",
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> processing().retry(
                "unsafe reference",
                null,
                NOW.plusSeconds(30)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsProcessingWithoutLeaseAndTerminalEvidenceMixes() {
        assertThatThrownBy(() -> new WithdrawalPayout(
                "10000000-0000-4000-8000-000000000001",
                "bank-provider",
                "withdrawal:10000000-0000-4000-8000-000000000001",
                WithdrawalPayout.State.PROCESSING,
                1,
                null,
                null,
                null,
                null,
                NOW,
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WithdrawalPayout(
                "10000000-0000-4000-8000-000000000001",
                "bank-provider",
                "withdrawal:10000000-0000-4000-8000-000000000001",
                WithdrawalPayout.State.PAID,
                1,
                "provider-001",
                null,
                null,
                null,
                NOW,
                NOW.plusSeconds(1),
                "20000000-0000-4000-8000-000000000001",
                "30000000-0000-4000-8000-000000000001"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WithdrawalPayout(
                "10000000-0000-4000-8000-000000000001",
                "bank-provider",
                "withdrawal:10000000-0000-4000-8000-000000000001",
                WithdrawalPayout.State.FAILED,
                1,
                null,
                "bad-error",
                null,
                null,
                NOW,
                NOW.plusSeconds(1),
                null,
                "30000000-0000-4000-8000-000000000001"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingProviderKeyAndExcessiveAttempts() {
        assertThatThrownBy(() -> candidate(
                null,
                "withdrawal:10000000-0000-4000-8000-000000000001",
                1
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> candidate(
                "bank-provider",
                null,
                1
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> candidate(
                "bank-provider",
                "withdrawal:10000000-0000-4000-8000-000000000001",
                10_001
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requiredCandidate(null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requiredCandidate(
                WithdrawalPayout.State.PROCESSING,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static WithdrawalPayout candidate(
            String provider,
            String key,
            int attempt
    ) {
        return new WithdrawalPayout(
                "10000000-0000-4000-8000-000000000001",
                provider,
                key,
                WithdrawalPayout.State.PROCESSING,
                attempt,
                null,
                null,
                null,
                NOW.plusSeconds(30),
                NOW,
                null,
                null,
                null
        );
    }

    private static WithdrawalPayout requiredCandidate(
            WithdrawalPayout.State state,
            Instant startedAt
    ) {
        return new WithdrawalPayout(
                "10000000-0000-4000-8000-000000000001",
                "bank-provider",
                "withdrawal:10000000-0000-4000-8000-000000000001",
                state,
                1,
                null,
                null,
                null,
                NOW.plusSeconds(30),
                startedAt,
                null,
                null,
                null
        );
    }

    private static WithdrawalPayout processing() {
        return new WithdrawalPayout(
                "10000000-0000-4000-8000-000000000001",
                "bank-provider",
                "withdrawal:10000000-0000-4000-8000-000000000001",
                WithdrawalPayout.State.PROCESSING,
                1,
                null,
                null,
                null,
                NOW.plusSeconds(30),
                NOW,
                null,
                null,
                null
        );
    }
}
