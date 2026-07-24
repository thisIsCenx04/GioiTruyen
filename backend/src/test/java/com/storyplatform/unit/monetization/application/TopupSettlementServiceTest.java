package com.storyplatform.unit.monetization.application;

import com.storyplatform.monetization.application.LedgerOperations;
import com.storyplatform.monetization.application.LedgerConflictException;
import com.storyplatform.monetization.application.TopupSettlementOperations;
import com.storyplatform.monetization.application.TopupSettlementService;
import com.storyplatform.monetization.application.WalletOperations;
import com.storyplatform.monetization.application.port.TopupSettlementRepository;
import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.LedgerTransaction;
import com.storyplatform.monetization.domain.PaymentEvent;
import com.storyplatform.monetization.domain.TopupRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TopupSettlementServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T00:00:00Z");
    private final TopupSettlementRepository repository =
            mock(TopupSettlementRepository.class);
    private final WalletOperations wallets = mock(WalletOperations.class);
    private final LedgerOperations ledger = mock(LedgerOperations.class);
    private final TopupSettlementService service =
            new TopupSettlementService(
                    repository,
                    wallets,
                    ledger,
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );

    @Test
    void matchesAmountAndReferenceThenCreditsSnapshotExactlyOnce() {
        PaymentEvent event = event(100_000, PaymentEvent.Status.RECEIVED);
        TopupRequest topup = topup();
        when(repository.findEvent("bank", "event-1"))
                .thenReturn(Optional.of(event));
        when(repository.findTopup(event.transferReference()))
                .thenReturn(Optional.of(topup));
        when(ledger.post(any())).thenReturn(posting(topup));
        when(repository.complete(any(), any(), any(), any()))
                .thenReturn(true);

        assertThat(service.settle("bank", "event-1"))
                .isEqualTo(TopupSettlementOperations.Result.CREDITED);

        verify(ledger).post(any());
        verify(repository).complete(
                event.id(),
                topup.id(),
                posting(topup).transaction().id(),
                NOW
        );
    }

    @Test
    void sendsMismatchesToReviewWithoutCrediting() {
        PaymentEvent event = event(99_999, PaymentEvent.Status.RECEIVED);
        when(repository.findEvent("bank", "event-1"))
                .thenReturn(Optional.of(event));
        when(repository.findTopup(event.transferReference()))
                .thenReturn(Optional.of(topup()));
        when(repository.flagForReview(any(), any(), any(), any()))
                .thenReturn(true);

        assertThat(service.settle("bank", "event-1"))
                .isEqualTo(TopupSettlementOperations.Result.PENDING_REVIEW);
        verify(ledger, never()).post(any());
        verify(repository).flagForReview(
                event.id(),
                topup().id(),
                "AMOUNT_MISMATCH",
                NOW
        );
    }

    @Test
    void treatsAnAlreadyDecidedEventAsReplay() {
        when(repository.findEvent("bank", "event-1"))
                .thenReturn(Optional.of(event(
                        100_000,
                        PaymentEvent.Status.MATCHED
                )));

        assertThat(service.settle("bank", "event-1"))
                .isEqualTo(TopupSettlementOperations.Result.REPLAYED);
        verify(repository, never()).findTopup(any());
    }

    @Test
    void abortsTheLedgerTransactionWhenConcurrentCasLoses() {
        PaymentEvent event = event(100_000, PaymentEvent.Status.RECEIVED);
        TopupRequest topup = topup();
        when(repository.findEvent("bank", "event-1"))
                .thenReturn(Optional.of(event));
        when(repository.findTopup(event.transferReference()))
                .thenReturn(Optional.of(topup));
        when(ledger.post(any())).thenReturn(posting(topup));

        assertThatThrownBy(() -> service.settle("bank", "event-1"))
                .isInstanceOf(LedgerConflictException.class)
                .hasMessageContaining("concurrent");
        verify(repository).complete(any(), any(), any(), any());
    }

    private static PaymentEvent event(
            long amount,
            PaymentEvent.Status status
    ) {
        return new PaymentEvent(
                "30000000-0000-4000-8000-000000000001",
                "bank",
                "event-1",
                "bank-ref-1",
                amount,
                "GT12345678901234",
                NOW.minusSeconds(30),
                NOW,
                "a".repeat(64),
                "{}",
                status
        );
    }

    private static TopupRequest topup() {
        return new TopupRequest(
                "20000000-0000-4000-8000-000000000001",
                "reader",
                100_000,
                90_000,
                BigDecimal.TEN,
                0,
                "GT12345678901234",
                "qr",
                TopupRequest.Status.AWAITING_PAYMENT,
                NOW.plusSeconds(60),
                NOW.minusSeconds(60),
                "b".repeat(64),
                "c".repeat(64)
        );
    }

    private static LedgerOperations.Posting posting(TopupRequest topup) {
        String platform = WalletOperations.accountId(
                com.storyplatform.monetization.domain.WalletAccount
                        .OwnerType.PLATFORM,
                "topup-clearing"
        );
        String user = WalletOperations.accountId(
                com.storyplatform.monetization.domain.WalletAccount
                        .OwnerType.USER,
                topup.userId()
        );
        return new LedgerOperations.Posting(
                LedgerTransaction.post(
                        "40000000-0000-4000-8000-000000000001",
                        LedgerTransaction.Type.TOPUP,
                        "topup_request",
                        topup.id(),
                        List.of(
                                new LedgerEntry(
                                        platform,
                                        LedgerEntry.Side.DEBIT,
                                        topup.creditedXu()
                                ),
                                new LedgerEntry(
                                        user,
                                        LedgerEntry.Side.CREDIT,
                                        topup.creditedXu()
                                )
                        ),
                        "d".repeat(64),
                        NOW
                ),
                false
        );
    }
}
