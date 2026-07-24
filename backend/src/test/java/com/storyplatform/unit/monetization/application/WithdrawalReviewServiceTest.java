package com.storyplatform.unit.monetization.application;

import com.storyplatform.monetization.application.LedgerOperations;
import com.storyplatform.monetization.application.WithdrawalException;
import com.storyplatform.monetization.application
        .WithdrawalReviewService;
import com.storyplatform.monetization.application.port.WithdrawalRepository;
import com.storyplatform.monetization.application.port
        .WithdrawalReviewAuthorizer;
import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.LedgerTransaction;
import com.storyplatform.monetization.domain.Withdrawal;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WithdrawalReviewServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T01:00:00Z");
    private static final String REQUESTER =
            "10000000-0000-4000-8000-000000000001";
    private static final String REVIEWER =
            "20000000-0000-4000-8000-000000000001";
    private static final String TEAM =
            "30000000-0000-4000-8000-000000000001";
    private static final String WITHDRAWAL =
            "40000000-0000-4000-8000-000000000001";
    private static final String ACCOUNT =
            "50000000-0000-4000-8000-000000000001";
    private static final String RESERVE =
            "60000000-0000-4000-8000-000000000001";
    private static final String RELEASE =
            "70000000-0000-4000-8000-000000000001";
    private static final String EVENT =
            "80000000-0000-4000-8000-000000000001";
    private static final String KEY = "withdrawal-review-key-0001";
    private static final String REASON =
            "Verified finance review evidence.";
    private final WithdrawalRepository repository =
            mock(WithdrawalRepository.class);
    private final WithdrawalReviewAuthorizer authorizer =
            mock(WithdrawalReviewAuthorizer.class);
    private final LedgerOperations ledger = mock(LedgerOperations.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final WithdrawalReviewService service =
            new WithdrawalReviewService(
                    repository,
                    authorizer,
                    ledger,
                    outbox,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    () -> UUID.fromString(EVENT)
            );

    @BeforeEach
    void setUp() {
        when(repository.findById(WITHDRAWAL))
                .thenReturn(Optional.of(pending()));
        when(repository.findByReviewKeyHash(any()))
                .thenReturn(Optional.empty());
        when(authorizer.consume(REVIEWER, "grant", WITHDRAWAL))
                .thenReturn(true);
        when(repository.decide(any())).thenReturn(true);
    }

    @Test
    void approvesWithSeparationReauthenticationAndIdempotentReplay() {
        AtomicReference<Withdrawal> decision = new AtomicReference<>();
        when(repository.decide(any())).thenAnswer(invocation -> {
            decision.set(invocation.getArgument(0));
            return true;
        });

        var approved = service.approve(
                REVIEWER, WITHDRAWAL, "grant", KEY, REASON
        );
        when(repository.findByReviewKeyHash(any()))
                .thenAnswer(invocation ->
                        Optional.ofNullable(decision.get()));
        var replayed = service.approve(
                REVIEWER, WITHDRAWAL, "consumed", KEY, REASON
        );

        assertThat(approved.state()).isEqualTo("APPROVED");
        assertThat(approved.releaseTransactionId()).isNull();
        assertThat(approved.riskLevel()).isEqualTo("STANDARD");
        assertThat(approved.riskRuleVersion())
                .isEqualTo("withdrawal-risk-2026.1");
        assertThat(replayed.replayed()).isTrue();
        verify(authorizer).consume(REVIEWER, "grant", WITHDRAWAL);
        verify(ledger, never()).post(any());
    }

    @Test
    void snapshotsHighValueRiskFromServerSideGrossAmount() {
        when(repository.findById(WITHDRAWAL))
                .thenReturn(Optional.of(pending(1_000_000)));

        var approved = service.approve(
                REVIEWER, WITHDRAWAL, "grant", KEY, REASON
        );

        assertThat(approved.riskLevel()).isEqualTo("HIGH_VALUE");
    }

    @Test
    void rejectionReleasesReservedGrossExactlyThroughLedgerBuckets() {
        when(ledger.post(any())).thenAnswer(invocation -> {
            LedgerOperations.Command command = invocation.getArgument(0);
            return new LedgerOperations.Posting(
                    LedgerTransaction.post(
                            RELEASE,
                            command.type(),
                            command.referenceType(),
                            command.referenceId(),
                            command.entries(),
                            command.idempotencyKeyHash(),
                            NOW
                    ),
                    false
            );
        });
        ArgumentCaptor<LedgerOperations.Command> command =
                ArgumentCaptor.forClass(LedgerOperations.Command.class);

        var result = service.reject(
                REVIEWER, WITHDRAWAL, "grant", KEY, REASON
        );

        assertThat(result.state()).isEqualTo("REJECTED");
        assertThat(result.releaseTransactionId()).isEqualTo(RELEASE);
        verify(ledger).post(command.capture());
        assertThat(command.getValue().type())
                .isEqualTo(LedgerTransaction.Type.WITHDRAWAL_RELEASE);
        assertThat(command.getValue().entries())
                .extracting(LedgerEntry::side, LedgerEntry::bucket)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                LedgerEntry.Side.DEBIT,
                                LedgerEntry.Bucket.RESERVED
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                LedgerEntry.Side.CREDIT,
                                LedgerEntry.Bucket.AVAILABLE
                        )
                );
    }

    @Test
    void blocksRequesterMissingGrantAndConcurrentDecision() {
        assertKind(() -> service.approve(
                REQUESTER, WITHDRAWAL, "grant", KEY, REASON
        ), WithdrawalException.Kind.FORBIDDEN);
        verify(authorizer, never()).consume(
                REQUESTER, "grant", WITHDRAWAL
        );

        when(authorizer.consume(REVIEWER, "grant", WITHDRAWAL))
                .thenReturn(false);
        assertKind(() -> service.approve(
                REVIEWER, WITHDRAWAL, "grant", KEY, REASON
        ), WithdrawalException.Kind.FORBIDDEN);

        when(authorizer.consume(REVIEWER, "grant", WITHDRAWAL))
                .thenReturn(true);
        when(repository.decide(any())).thenReturn(false);
        assertKind(() -> service.approve(
                REVIEWER, WITHDRAWAL, "grant", KEY, REASON
        ), WithdrawalException.Kind.CONFLICT);
    }

    @Test
    void rejectsMissingWithdrawalInvalidReasonAndChangedReplay() {
        when(repository.findById(WITHDRAWAL)).thenReturn(Optional.empty());
        assertKind(() -> service.approve(
                REVIEWER, WITHDRAWAL, "grant", KEY, REASON
        ), WithdrawalException.Kind.NOT_FOUND);

        when(repository.findById(WITHDRAWAL))
                .thenReturn(Optional.of(pending()));
        assertKind(() -> service.approve(
                REVIEWER, WITHDRAWAL, "grant", KEY, "short"
        ), WithdrawalException.Kind.INVALID);
        Withdrawal approved = pending().approved(
                REVIEWER,
                REASON,
                "STANDARD",
                "withdrawal-risk-2026.1",
                "a".repeat(64),
                "b".repeat(64),
                NOW
        );
        when(repository.findByReviewKeyHash(any()))
                .thenReturn(Optional.of(approved));
        assertKind(() -> service.approve(
                REVIEWER, WITHDRAWAL, "grant", KEY,
                "Another valid finance review reason."
        ), WithdrawalException.Kind.CONFLICT);
    }

    private static Withdrawal pending() {
        return pending(100_000);
    }

    private static Withdrawal pending(long gross) {
        long fee = gross < 1_000_000 ? 20_000 : 0;
        return new Withdrawal(
                WITHDRAWAL,
                TEAM,
                ACCOUNT,
                gross,
                fee,
                gross - fee,
                "withdrawal-fee-2026.1",
                new Withdrawal.DestinationSnapshot(
                        "90000000-0000-4000-8000-000000000001",
                        "VCB •••• 1234",
                        "encrypted:v1:ciphertext",
                        1,
                        NOW.minusSeconds(86_400)
                ),
                Withdrawal.State.PENDING_REVIEW,
                REQUESTER,
                RESERVE,
                "c".repeat(64),
                "d".repeat(64),
                NOW.minusSeconds(60),
                null, null, null, null, null, null, null, null
        );
    }

    private static void assertKind(
            Runnable operation,
            WithdrawalException.Kind kind
    ) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(WithdrawalException.class)
                .extracting("kind")
                .isEqualTo(kind);
    }
}
