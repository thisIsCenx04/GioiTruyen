package com.storyplatform.unit.monetization.application;

import com.storyplatform.monetization.application.WithdrawalException;
import com.storyplatform.monetization.application
        .WithdrawalPayoutCallbackService;
import com.storyplatform.monetization.application
        .WithdrawalPayoutCompletionOperations;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutCallbackDecoder;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutCallbackRepository;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutCallbackVerifier;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutGateway;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutRepository;
import com.storyplatform.monetization.application.port.WithdrawalRepository;
import com.storyplatform.monetization.domain.Withdrawal;
import com.storyplatform.monetization.domain.WithdrawalPayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WithdrawalPayoutCallbackServiceTest {

    private static final String WITHDRAWAL =
            "10000000-0000-4000-8000-000000000001";
    private static final Instant NOW =
            Instant.parse("2026-07-25T01:00:00Z");
    private static final byte[] BODY = new byte[]{1, 2, 3};
    private final WithdrawalPayoutCallbackVerifier verifier =
            mock(WithdrawalPayoutCallbackVerifier.class);
    private final WithdrawalPayoutCallbackDecoder decoder =
            mock(WithdrawalPayoutCallbackDecoder.class);
    private final WithdrawalPayoutCallbackRepository callbacks =
            mock(WithdrawalPayoutCallbackRepository.class);
    private final WithdrawalRepository withdrawals =
            mock(WithdrawalRepository.class);
    private final WithdrawalPayoutRepository payouts =
            mock(WithdrawalPayoutRepository.class);
    private final WithdrawalPayoutCompletionOperations completions =
            mock(WithdrawalPayoutCompletionOperations.class);
    private final Withdrawal withdrawal = mock(Withdrawal.class);
    private final WithdrawalPayout payout = mock(WithdrawalPayout.class);

    @BeforeEach
    void setUp() {
        when(verifier.verify(
                "bank-provider", BODY, "timestamp", "signature"
        )).thenReturn(true);
        when(callbacks.find("bank-provider", "event-001"))
                .thenReturn(Optional.empty());
        when(decoder.decode(BODY)).thenReturn(callback(
                WithdrawalPayoutGateway.Status.PAID
        ));
        when(payouts.findByWithdrawalId(WITHDRAWAL))
                .thenReturn(Optional.of(payout));
        when(withdrawals.findById(WITHDRAWAL))
                .thenReturn(Optional.of(withdrawal));
        when(payout.state()).thenReturn(WithdrawalPayout.State.PROCESSING);
        when(withdrawal.state()).thenReturn(Withdrawal.State.PROCESSING);
    }

    @Test
    void storesAuthenticatedEventBeforeConvergingOnCompletion() {
        service().accept(
                "bank-provider", BODY, "timestamp", "signature"
        );

        var receipt = ArgumentCaptor.forClass(
                WithdrawalPayoutCallbackRepository.Receipt.class
        );
        verify(callbacks).insert(receipt.capture());
        assertThat(receipt.getValue().eventId()).isEqualTo("event-001");
        assertThat(receipt.getValue().requestHash()).hasSize(64);
        verify(completions).complete(any(), any());
    }

    @Test
    void exactReplayIsAcknowledgedWithoutRepeatingSideEffects() {
        service().accept(
                "bank-provider", BODY, "timestamp", "signature"
        );
        var receipt = ArgumentCaptor.forClass(
                WithdrawalPayoutCallbackRepository.Receipt.class
        );
        verify(callbacks).insert(receipt.capture());
        when(callbacks.find("bank-provider", "event-001"))
                .thenReturn(Optional.of(receipt.getValue()));

        service().accept(
                "bank-provider", BODY, "timestamp", "signature"
        );

        verify(completions).complete(any(), any());
    }

    @Test
    void rejectsBadSignatureAndChangedReplay() {
        when(verifier.verify(
                "bank-provider", BODY, "timestamp", "signature"
        )).thenReturn(false);
        assertKind(
                () -> service().accept(
                        "bank-provider", BODY, "timestamp", "signature"
                ),
                WithdrawalException.Kind.FORBIDDEN
        );

        when(verifier.verify(
                "bank-provider", BODY, "timestamp", "signature"
        )).thenReturn(true);
        when(callbacks.find("bank-provider", "event-001"))
                .thenReturn(Optional.of(
                        new WithdrawalPayoutCallbackRepository.Receipt(
                                UUID.randomUUID().toString(),
                                "bank-provider",
                                "event-001",
                                WITHDRAWAL,
                                "0".repeat(64),
                                NOW
                        )
                ));
        assertKind(
                () -> service().accept(
                        "bank-provider", BODY, "timestamp", "signature"
                ),
                WithdrawalException.Kind.CONFLICT
        );
    }

    @Test
    void acceptsMatchingTerminalCallbackAndRejectsConflict() {
        when(payout.state()).thenReturn(WithdrawalPayout.State.PAID);

        service().accept(
                "bank-provider", BODY, "timestamp", "signature"
        );
        verify(callbacks).insert(any());
        verify(completions, never()).complete(any(), any());

        when(decoder.decode(BODY)).thenReturn(callback(
                WithdrawalPayoutGateway.Status.FAILED
        ));
        assertKind(
                () -> service().accept(
                        "bank-provider", BODY, "timestamp", "signature"
                ),
                WithdrawalException.Kind.CONFLICT
        );
    }

    private WithdrawalPayoutCallbackService service() {
        return new WithdrawalPayoutCallbackService(
                verifier,
                decoder,
                callbacks,
                withdrawals,
                payouts,
                completions,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> UUID.fromString(
                        "20000000-0000-4000-8000-000000000001"
                )
        );
    }

    private static WithdrawalPayoutCallbackDecoder.Callback callback(
            WithdrawalPayoutGateway.Status status
    ) {
        return new WithdrawalPayoutCallbackDecoder.Callback(
                "event-001",
                WITHDRAWAL,
                "provider-001",
                status,
                status == WithdrawalPayoutGateway.Status.FAILED
                        ? "ACCOUNT_REJECTED"
                        : null,
                NOW
        );
    }

    private static void assertKind(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            WithdrawalException.Kind kind
    ) {
        assertThatThrownBy(action)
                .isInstanceOf(WithdrawalException.class)
                .extracting("kind")
                .isEqualTo(kind);
    }
}
