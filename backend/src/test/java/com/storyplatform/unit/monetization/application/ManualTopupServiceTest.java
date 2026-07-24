package com.storyplatform.unit.monetization.application;

import com.storyplatform.monetization.application.LedgerOperations;
import com.storyplatform.monetization.application.ManualTopupException;
import com.storyplatform.monetization.application.ManualTopupService;
import com.storyplatform.monetization.application.WalletOperations;
import com.storyplatform.monetization.application.port.ManualTopupAuthorizer;
import com.storyplatform.monetization.application.port.ManualTopupRepository;
import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.LedgerTransaction;
import com.storyplatform.monetization.domain.PaymentEvent;
import com.storyplatform.monetization.domain.TopupRequest;
import com.storyplatform.monetization.domain.WalletAccount;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManualTopupServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T00:00:00Z");
    private final ManualTopupRepository repository =
            mock(ManualTopupRepository.class);
    private final ManualTopupAuthorizer authorizer =
            mock(ManualTopupAuthorizer.class);
    private final WalletOperations wallets = mock(WalletOperations.class);
    private final LedgerOperations ledger = mock(LedgerOperations.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final ManualTopupService service = new ManualTopupService(
            repository,
            authorizer,
            wallets,
            ledger,
            outbox,
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> UUID.fromString(
                    "50000000-0000-4000-8000-000000000001"
            )
    );

    @Test
    void approvesWithScopedGrantAuditCasAndSnapshotCredit() {
        stubPending();
        when(authorizer.consume("admin", "grant", topup().id()))
                .thenReturn(true);
        when(ledger.post(any())).thenReturn(posting());
        when(repository.complete(
                any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(true);
        ArgumentCaptor<LedgerOperations.Command> command =
                ArgumentCaptor.forClass(LedgerOperations.Command.class);
        ArgumentCaptor<IntegrationEvent> event =
                ArgumentCaptor.forClass(IntegrationEvent.class);

        var result = service.approve(
                "admin",
                topup().id(),
                "grant",
                "Verified settlement statement",
                "evidence/bank-statement-1"
        );

        assertThat(result.status()).isEqualTo("CREDITED");
        verify(ledger).post(command.capture());
        assertThat(command.getValue().entries())
                .extracting(LedgerEntry::amountXu)
                .containsOnly(90_000L);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().eventType())
                .isEqualTo("monetization.topup.manuallyapproved");
    }

    @Test
    void rejectsMissingGrantBeforeAnyLedgerWrite() {
        stubPending();

        assertThatThrownBy(() -> service.approve(
                "admin",
                topup().id(),
                "grant",
                "Verified settlement statement",
                "evidence/bank-statement-1"
        )).isInstanceOf(ManualTopupException.class)
                .extracting("kind")
                .isEqualTo(ManualTopupException.Kind.FORBIDDEN);
        verify(ledger, never()).post(any());
    }

    @Test
    void rollsBackWhenAutomationWinsTheFinalCas() {
        stubPending();
        when(authorizer.consume("admin", "grant", topup().id()))
                .thenReturn(true);
        when(ledger.post(any())).thenReturn(posting());

        assertThatThrownBy(() -> service.approve(
                "admin",
                topup().id(),
                "grant",
                "Verified settlement statement",
                "evidence/bank-statement-1"
        )).isInstanceOf(ManualTopupException.class)
                .extracting("kind")
                .isEqualTo(ManualTopupException.Kind.CONFLICT);
        verify(outbox, never()).append(any());
    }

    private void stubPending() {
        when(repository.findPendingTopup(topup().id()))
                .thenReturn(Optional.of(topup()));
        when(repository.findPendingEvent(topup().id()))
                .thenReturn(Optional.of(payment()));
    }

    private static TopupRequest topup() {
        return new TopupRequest(
                "20000000-0000-4000-8000-000000000001",
                "reader",
                100_000,
                90_000,
                BigDecimal.TEN,
                1,
                "GT12345678901234",
                "qr",
                TopupRequest.Status.PENDING_REVIEW,
                NOW,
                NOW.minusSeconds(1800),
                "a".repeat(64),
                "b".repeat(64)
        );
    }

    private static PaymentEvent payment() {
        return new PaymentEvent(
                "30000000-0000-4000-8000-000000000001",
                "bank",
                "event-1",
                "bank-ref",
                100_000,
                topup().transferReference(),
                NOW.minusSeconds(60),
                NOW.minusSeconds(30),
                "c".repeat(64),
                "{}",
                PaymentEvent.Status.PENDING_REVIEW
        );
    }

    private static LedgerOperations.Posting posting() {
        return new LedgerOperations.Posting(
                LedgerTransaction.post(
                        "40000000-0000-4000-8000-000000000001",
                        LedgerTransaction.Type.TOPUP,
                        "topup_request",
                        topup().id(),
                        List.of(
                                new LedgerEntry(
                                        WalletOperations.accountId(
                                                WalletAccount.OwnerType.PLATFORM,
                                                "topup-clearing"
                                        ),
                                        LedgerEntry.Side.DEBIT,
                                        90_000
                                ),
                                new LedgerEntry(
                                        WalletOperations.accountId(
                                                WalletAccount.OwnerType.USER,
                                                "reader"
                                        ),
                                        LedgerEntry.Side.CREDIT,
                                        90_000
                                )
                        ),
                        "d".repeat(64),
                        NOW
                ),
                false
        );
    }
}
