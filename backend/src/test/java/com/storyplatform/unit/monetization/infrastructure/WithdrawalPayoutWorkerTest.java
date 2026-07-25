package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.monetization.application
        .WithdrawalPayoutClaimOperations;
import com.storyplatform.monetization.application
        .WithdrawalPayoutCompletionOperations;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutGateway;
import com.storyplatform.monetization.domain.Withdrawal;
import com.storyplatform.monetization.domain.WithdrawalPayout;
import com.storyplatform.monetization.infrastructure.WithdrawalPayoutWorker;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WithdrawalPayoutWorkerTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T01:00:00Z");
    private final WithdrawalPayoutClaimOperations claims =
            mock(WithdrawalPayoutClaimOperations.class);
    private final WithdrawalPayoutCompletionOperations completions =
            mock(WithdrawalPayoutCompletionOperations.class);
    private final WithdrawalPayoutGateway gateway =
            mock(WithdrawalPayoutGateway.class);

    @Test
    void submitsProviderCommandAndCompletesClaim() {
        var claim = claim();
        var result = new WithdrawalPayoutGateway.Result(
                WithdrawalPayoutGateway.Status.PAID,
                "provider-001",
                null
        );
        when(claims.claim())
                .thenReturn(Optional.of(claim), Optional.empty());
        when(gateway.submit(any())).thenReturn(result);
        var command = ArgumentCaptor.forClass(
                WithdrawalPayoutGateway.Command.class
        );

        new WithdrawalPayoutWorker(
                claims, completions, gateway, 2
        ).process();

        verify(gateway).submit(command.capture());
        assertThat(command.getValue().withdrawalId())
                .isEqualTo(claim.withdrawal().id());
        assertThat(command.getValue().idempotencyKey())
                .isEqualTo(claim.payout().providerIdempotencyKey());
        assertThat(command.getValue().amountVnd()).isEqualTo(80_000);
        verify(completions).complete(claim, result);
    }

    @Test
    void ambiguousProviderFailureSchedulesRetryAndContinues() {
        var claim = claim();
        when(claims.claim())
                .thenReturn(Optional.of(claim), Optional.empty());
        when(gateway.submit(any()))
                .thenThrow(new IllegalStateException("timeout"));

        new WithdrawalPayoutWorker(
                claims, completions, gateway, 2
        ).process();

        verify(completions).retry(claim, "PROVIDER_UNAVAILABLE");
        verify(completions, never()).complete(any(), any());
    }

    @Test
    void validatesBatchBoundary() {
        assertThatThrownBy(() -> new WithdrawalPayoutWorker(
                claims, completions, gateway, 0
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static WithdrawalPayoutClaimOperations.Claim claim() {
        Withdrawal withdrawal = pending().approved(
                "70000000-0000-4000-8000-000000000001",
                "Verified finance approval.",
                "STANDARD",
                "withdrawal-risk-2026.1",
                "e".repeat(64),
                "f".repeat(64),
                NOW.minusSeconds(60)
        ).processing();
        WithdrawalPayout payout = new WithdrawalPayout(
                withdrawal.id(),
                "bank-provider",
                "withdrawal:" + withdrawal.id(),
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
        return new WithdrawalPayoutClaimOperations.Claim(
                withdrawal,
                payout
        );
    }

    private static Withdrawal pending() {
        return new Withdrawal(
                "10000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                "30000000-0000-4000-8000-000000000001",
                100_000,
                20_000,
                80_000,
                "withdrawal-fee-2026.1",
                new Withdrawal.DestinationSnapshot(
                        "60000000-0000-4000-8000-000000000001",
                        "VCB **** 1234",
                        "encrypted:v1:provider-token",
                        1,
                        NOW.minusSeconds(86_400)
                ),
                Withdrawal.State.PENDING_REVIEW,
                "80000000-0000-4000-8000-000000000001",
                "90000000-0000-4000-8000-000000000001",
                "a".repeat(64),
                "b".repeat(64),
                NOW.minusSeconds(120),
                null, null, null, null, null, null, null, null
        );
    }
}
