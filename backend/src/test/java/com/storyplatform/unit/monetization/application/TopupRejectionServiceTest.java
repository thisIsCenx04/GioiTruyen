package com.storyplatform.unit.monetization.application;

import com.storyplatform.monetization.application.ManualTopupException;
import com.storyplatform.monetization.application.TopupRejectionOperations;
import com.storyplatform.monetization.application.TopupRejectionService;
import com.storyplatform.monetization.application.port.TopupRejectionRepository;
import com.storyplatform.monetization.domain.PaymentEvent;
import com.storyplatform.monetization.domain.TopupRequest;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
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

class TopupRejectionServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T00:00:00Z");
    private final TopupRejectionRepository repository =
            mock(TopupRejectionRepository.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final TopupRejectionService service =
            new TopupRejectionService(
                    repository,
                    outbox,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    () -> UUID.fromString(
                            "50000000-0000-4000-8000-000000000001"
                    )
            );

    @Test
    void rejectsPendingReviewAndEmitsMinimalNotification() {
        var review = review(
                TopupRequest.Status.PENDING_REVIEW,
                PaymentEvent.Status.PENDING_REVIEW
        );
        when(repository.find(review.topup().id()))
                .thenReturn(Optional.of(review));
        when(repository.reject(
                any(), any(), any(), any(), any(), any()
        )).thenReturn(true);
        ArgumentCaptor<IntegrationEvent> event =
                ArgumentCaptor.forClass(IntegrationEvent.class);

        var result = reject();

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.replayed()).isFalse();
        verify(outbox).append(event.capture());
        assertThat(event.getValue().eventType())
                .isEqualTo("monetization.topup.rejected");
        assertThat(event.getValue().payload().toString())
                .doesNotContain("Settlement evidence verified");
    }

    @Test
    void replaysAnExistingRejectionWithoutAuditOrNotification() {
        var review = review(
                TopupRequest.Status.REJECTED,
                PaymentEvent.Status.REJECTED
        );
        when(repository.find(review.topup().id()))
                .thenReturn(Optional.of(review));

        assertThat(reject().replayed()).isTrue();
        verify(repository, never()).reject(
                any(), any(), any(), any(), any(), any()
        );
        verify(outbox, never()).append(any());
    }

    @Test
    void blocksApprovalOrAutomationFinalStates() {
        var review = review(
                TopupRequest.Status.CREDITED,
                PaymentEvent.Status.MATCHED
        );
        when(repository.find(review.topup().id()))
                .thenReturn(Optional.of(review));

        assertThatThrownBy(this::reject)
                .isInstanceOf(ManualTopupException.class)
                .extracting("kind")
                .isEqualTo(ManualTopupException.Kind.CONFLICT);
        verify(outbox, never()).append(any());
    }

    @Test
    void validatesEvidenceAndMissingReviewBeforeMutation() {
        assertThatThrownBy(() -> service.reject(
                "admin",
                topup(TopupRequest.Status.PENDING_REVIEW).id(),
                TopupRejectionOperations.ReasonCode.OTHER,
                "short",
                "bad evidence"
        )).isInstanceOf(ManualTopupException.class)
                .extracting("kind")
                .isEqualTo(ManualTopupException.Kind.INVALID);
        assertThatThrownBy(() -> service.reject(
                "admin",
                topup(TopupRequest.Status.PENDING_REVIEW).id(),
                TopupRejectionOperations.ReasonCode.OTHER,
                "Settlement evidence verified",
                "bad evidence"
        )).isInstanceOf(ManualTopupException.class)
                .extracting("kind")
                .isEqualTo(ManualTopupException.Kind.INVALID);

        assertThatThrownBy(this::reject)
                .isInstanceOf(ManualTopupException.class)
                .extracting("kind")
                .isEqualTo(ManualTopupException.Kind.NOT_FOUND);
        verify(repository, never()).reject(
                any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void returnsReplayWhenConcurrentRejectionWinsCas() {
        var pending = review(
                TopupRequest.Status.PENDING_REVIEW,
                PaymentEvent.Status.PENDING_REVIEW
        );
        var rejected = review(
                TopupRequest.Status.REJECTED,
                PaymentEvent.Status.REJECTED
        );
        when(repository.find(pending.topup().id()))
                .thenReturn(
                        Optional.of(pending),
                        Optional.of(rejected)
                );

        assertThat(reject().replayed()).isTrue();
        verify(outbox, never()).append(any());
    }

    private TopupRejectionOperations.Rejection reject() {
        return service.reject(
                "admin",
                topup(TopupRequest.Status.PENDING_REVIEW).id(),
                TopupRejectionOperations.ReasonCode.AMOUNT_MISMATCH,
                "Settlement evidence verified",
                "evidence/bank-statement-1"
        );
    }

    private static TopupRejectionRepository.Review review(
            TopupRequest.Status topupStatus,
            PaymentEvent.Status eventStatus
    ) {
        return new TopupRejectionRepository.Review(
                topup(topupStatus),
                payment(eventStatus)
        );
    }

    private static TopupRequest topup(TopupRequest.Status status) {
        return new TopupRequest(
                "20000000-0000-4000-8000-000000000001",
                "reader",
                100_000,
                90_000,
                BigDecimal.TEN,
                1,
                "GT12345678901234",
                "qr",
                status,
                NOW,
                NOW.minusSeconds(1800),
                "a".repeat(64),
                "b".repeat(64)
        );
    }

    private static PaymentEvent payment(PaymentEvent.Status status) {
        return new PaymentEvent(
                "30000000-0000-4000-8000-000000000001",
                "bank",
                "event-1",
                "bank-ref",
                100_000,
                "GT12345678901234",
                NOW.minusSeconds(60),
                NOW.minusSeconds(30),
                "c".repeat(64),
                "{}",
                status
        );
    }
}
