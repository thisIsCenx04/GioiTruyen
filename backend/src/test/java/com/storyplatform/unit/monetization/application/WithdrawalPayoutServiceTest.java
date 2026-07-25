package com.storyplatform.unit.monetization.application;

import com.storyplatform.monetization.application.LedgerOperations;
import com.storyplatform.monetization.application.WalletOperations;
import com.storyplatform.monetization.application.WithdrawalException;
import com.storyplatform.monetization.application
        .WithdrawalPayoutClaimOperations;
import com.storyplatform.monetization.application
        .WithdrawalPayoutClaimService;
import com.storyplatform.monetization.application
        .WithdrawalPayoutCompletionService;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutGateway;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutRepository;
import com.storyplatform.monetization.application.port.WithdrawalRepository;
import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.LedgerTransaction;
import com.storyplatform.monetization.domain.Withdrawal;
import com.storyplatform.monetization.domain.WithdrawalPayout;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WithdrawalPayoutServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T01:00:00Z");
    private static final String WITHDRAWAL =
            "10000000-0000-4000-8000-000000000001";
    private static final String TEAM =
            "20000000-0000-4000-8000-000000000001";
    private static final String ACCOUNT =
            "30000000-0000-4000-8000-000000000001";
    private static final String LEDGER =
            "40000000-0000-4000-8000-000000000001";
    private static final String EVENT =
            "50000000-0000-4000-8000-000000000001";
    private final WithdrawalRepository withdrawals =
            mock(WithdrawalRepository.class);
    private final WithdrawalPayoutRepository payouts =
            mock(WithdrawalPayoutRepository.class);
    private final WalletOperations wallets = mock(WalletOperations.class);
    private final LedgerOperations ledger = mock(LedgerOperations.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);

    @BeforeEach
    void setUp() {
        when(payouts.update(any(), any())).thenReturn(true);
        when(withdrawals.transition(any(), any(), any(), any()))
                .thenReturn(true);
        when(ledger.post(any())).thenAnswer(invocation ->
                posting(invocation.getArgument(0)));
    }

    @Test
    void atomicallyClaimsApprovedAndPrioritizesRetryablePayout() {
        var service = claims();
        when(payouts.claimRetryable(any(), any()))
                .thenReturn(Optional.empty());
        when(withdrawals.findOldestApproved())
                .thenReturn(Optional.of(approved()));
        when(payouts.insert(any())).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        var first = service.claim().orElseThrow();

        assertThat(first.withdrawal().state())
                .isEqualTo(Withdrawal.State.PROCESSING);
        assertThat(first.payout().attempt()).isOne();
        assertThat(first.payout().providerIdempotencyKey())
                .isEqualTo("withdrawal:" + WITHDRAWAL);

        WithdrawalPayout retry = payout().retry(
                "provider-001",
                "TIMEOUT",
                NOW
        );
        retry = new WithdrawalPayout(
                retry.withdrawalId(), retry.provider(),
                retry.providerIdempotencyKey(), retry.state(), 2,
                retry.providerReference(), retry.lastErrorCode(),
                null, NOW.plusSeconds(30), retry.startedAt(),
                null, null, null
        );
        when(payouts.claimRetryable(any(), any()))
                .thenReturn(Optional.of(retry));
        when(withdrawals.findById(WITHDRAWAL))
                .thenReturn(Optional.of(processing()));

        assertThat(service.claim().orElseThrow().payout().attempt())
                .isEqualTo(2);
    }

    @Test
    void claimReturnsEmptyAndReportsCasLoser() {
        var service = claims();
        when(payouts.claimRetryable(any(), any()))
                .thenReturn(Optional.empty());
        when(withdrawals.findOldestApproved())
                .thenReturn(Optional.empty());
        assertThat(service.claim()).isEmpty();

        when(withdrawals.findOldestApproved())
                .thenReturn(Optional.of(approved()));
        when(withdrawals.transition(any(), any(), any(), any()))
                .thenReturn(false);
        assertThatThrownBy(service::claim)
                .isInstanceOf(WithdrawalException.class)
                .extracting("kind")
                .isEqualTo(WithdrawalException.Kind.CONFLICT);
    }

    @Test
    void validatesClaimConfigurationAndRetryConsistency() {
        assertThatThrownBy(() -> new WithdrawalPayoutClaimService(
                withdrawals,
                payouts,
                "INVALID",
                Duration.ofSeconds(30),
                Clock.fixed(NOW, ZoneOffset.UTC)
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WithdrawalPayoutClaimService(
                withdrawals,
                payouts,
                "bank-provider",
                Duration.ZERO,
                Clock.fixed(NOW, ZoneOffset.UTC)
        )).isInstanceOf(IllegalArgumentException.class);

        when(payouts.claimRetryable(any(), any()))
                .thenReturn(Optional.of(payout()));
        when(withdrawals.findById(WITHDRAWAL))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> claims().claim())
                .isInstanceOf(IllegalStateException.class);
        when(withdrawals.findById(WITHDRAWAL))
                .thenReturn(Optional.of(approved()));
        assertThatThrownBy(() -> claims().claim())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void paidResultSettlesGrossIntoNetAndVersionedFeeBuckets() {
        var service = completions();
        ArgumentCaptor<LedgerOperations.Command> command =
                ArgumentCaptor.forClass(LedgerOperations.Command.class);

        service.complete(
                claim(processing(), payout()),
                new WithdrawalPayoutGateway.Result(
                        WithdrawalPayoutGateway.Status.PAID,
                        "provider-001",
                        null
                )
        );

        verify(ledger).post(command.capture());
        assertThat(command.getValue().type())
                .isEqualTo(LedgerTransaction.Type.WITHDRAWAL_SETTLEMENT);
        assertThat(command.getValue().entries())
                .extracting(LedgerEntry::amountXu)
                .containsExactly(100_000L, 80_000L, 20_000L);
        verify(withdrawals).transition(
                WITHDRAWAL,
                Withdrawal.State.PROCESSING,
                Withdrawal.State.PAID,
                null
        );
    }

    @Test
    void explicitProviderFailureReleasesReserveAndBecomesTerminal() {
        var service = completions();
        ArgumentCaptor<LedgerOperations.Command> command =
                ArgumentCaptor.forClass(LedgerOperations.Command.class);

        service.complete(
                claim(processing(), payout()),
                new WithdrawalPayoutGateway.Result(
                        WithdrawalPayoutGateway.Status.FAILED,
                        "provider-001",
                        "ACCOUNT_REJECTED"
                )
        );

        verify(ledger).post(command.capture());
        assertThat(command.getValue().type())
                .isEqualTo(LedgerTransaction.Type.WITHDRAWAL_RELEASE);
        assertThat(command.getValue().entries())
                .extracting(LedgerEntry::bucket)
                .containsExactly(
                        LedgerEntry.Bucket.RESERVED,
                        LedgerEntry.Bucket.AVAILABLE
                );
        verify(withdrawals).transition(
                WITHDRAWAL,
                Withdrawal.State.PROCESSING,
                Withdrawal.State.FAILED,
                LEDGER
        );
    }

    @Test
    void pendingAndAmbiguousFailuresOnlyScheduleIdempotentRetry() {
        var service = completions();
        var claim = claim(processing(), payout());

        service.complete(
                claim,
                new WithdrawalPayoutGateway.Result(
                        WithdrawalPayoutGateway.Status.PENDING,
                        "provider-001",
                        null
                )
        );
        service.retry(claim, "PROVIDER_UNAVAILABLE");

        ArgumentCaptor<WithdrawalPayout> replacement =
                ArgumentCaptor.forClass(WithdrawalPayout.class);
        verify(payouts, org.mockito.Mockito.times(2))
                .update(any(), replacement.capture());
        assertThat(replacement.getAllValues())
                .allSatisfy(value -> {
                    assertThat(value.state())
                            .isEqualTo(WithdrawalPayout.State.PROCESSING);
                    assertThat(value.nextAttemptAt()).isAfter(NOW);
                    assertThat(value.leaseUntil()).isNull();
                });
    }

    @Test
    void completionDetectsEveryConcurrentTerminalRace() {
        var service = completions();
        var claim = claim(processing(), payout());
        var paid = new WithdrawalPayoutGateway.Result(
                WithdrawalPayoutGateway.Status.PAID,
                "provider-001",
                null
        );

        when(payouts.update(any(), any())).thenReturn(false);
        assertThatThrownBy(() -> service.complete(claim, paid))
                .isInstanceOf(WithdrawalException.class)
                .extracting("kind")
                .isEqualTo(WithdrawalException.Kind.CONFLICT);

        when(payouts.update(any(), any())).thenReturn(true);
        when(withdrawals.transition(any(), any(), any(), any()))
                .thenReturn(false);
        assertThatThrownBy(() -> service.complete(claim, paid))
                .isInstanceOf(WithdrawalException.class)
                .extracting("kind")
                .isEqualTo(WithdrawalException.Kind.CONFLICT);
    }

    @Test
    void zeroFeeSettlementUsesOnlyClearingLiability() {
        Withdrawal withdrawal = pending(1_000_000, 0).approved(
                "70000000-0000-4000-8000-000000000001",
                "Verified finance approval.",
                "STANDARD",
                "withdrawal-risk-2026.1",
                "e".repeat(64),
                "f".repeat(64),
                NOW.minusSeconds(60)
        ).processing();
        ArgumentCaptor<LedgerOperations.Command> command =
                ArgumentCaptor.forClass(LedgerOperations.Command.class);

        completions().complete(
                claim(withdrawal, payout()),
                new WithdrawalPayoutGateway.Result(
                        WithdrawalPayoutGateway.Status.PAID,
                        "provider-001",
                        null
                )
        );

        verify(ledger).post(command.capture());
        assertThat(command.getValue().entries()).hasSize(2);
    }

    private WithdrawalPayoutClaimService claims() {
        return new WithdrawalPayoutClaimService(
                withdrawals,
                payouts,
                "bank-provider",
                Duration.ofSeconds(30),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private WithdrawalPayoutCompletionService completions() {
        return new WithdrawalPayoutCompletionService(
                withdrawals,
                payouts,
                wallets,
                ledger,
                outbox,
                Duration.ofSeconds(30),
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> UUID.fromString(EVENT)
        );
    }

    private static WithdrawalPayoutClaimOperations.Claim claim(
            Withdrawal withdrawal,
            WithdrawalPayout payout
    ) {
        return new WithdrawalPayoutClaimOperations.Claim(
                withdrawal,
                payout
        );
    }

    private static LedgerOperations.Posting posting(
            LedgerOperations.Command command
    ) {
        return new LedgerOperations.Posting(
                LedgerTransaction.post(
                        LEDGER,
                        command.type(),
                        command.referenceType(),
                        command.referenceId(),
                        command.entries(),
                        command.idempotencyKeyHash(),
                        NOW
                ),
                false
        );
    }

    private static WithdrawalPayout payout() {
        return new WithdrawalPayout(
                WITHDRAWAL,
                "bank-provider",
                "withdrawal:" + WITHDRAWAL,
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

    private static Withdrawal approved() {
        return pending().approved(
                "70000000-0000-4000-8000-000000000001",
                "Verified finance approval.",
                "STANDARD",
                "withdrawal-risk-2026.1",
                "e".repeat(64),
                "f".repeat(64),
                NOW.minusSeconds(60)
        );
    }

    private static Withdrawal processing() {
        return approved().processing();
    }

    private static Withdrawal pending() {
        return pending(100_000, 20_000);
    }

    private static Withdrawal pending(long gross, long fee) {
        return new Withdrawal(
                WITHDRAWAL,
                TEAM,
                ACCOUNT,
                gross,
                fee,
                gross - fee,
                "withdrawal-fee-2026.1",
                new Withdrawal.DestinationSnapshot(
                        "60000000-0000-4000-8000-000000000001",
                        "VCB •••• 1234",
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
