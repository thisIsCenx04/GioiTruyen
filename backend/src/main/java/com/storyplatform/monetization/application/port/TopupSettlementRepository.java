package com.storyplatform.monetization.application.port;

import com.storyplatform.monetization.domain.PaymentEvent;
import com.storyplatform.monetization.domain.TopupRequest;

import java.time.Instant;
import java.util.Optional;

public interface TopupSettlementRepository {

    Optional<PaymentEvent> findEvent(String provider, String eventId);

    Optional<TopupRequest> findTopup(String transferReference);

    Optional<PaymentEvent> findOldestReceived(String provider);

    boolean complete(
            String eventId,
            String topupId,
            String ledgerTransactionId,
            Instant settledAt
    );

    boolean flagForReview(
            String eventId,
            String topupId,
            String reason,
            Instant reviewedAt
    );
}
