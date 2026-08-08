package com.storyplatform.monetization.application.port;

import com.storyplatform.monetization.domain.PaymentEvent;
import com.storyplatform.monetization.domain.TopupRequest;

import java.time.Instant;
import java.util.Optional;

public interface ManualTopupRepository {

    Optional<TopupRequest> findPendingTopup(String topupId);

    Optional<PaymentEvent> findPendingEvent(String topupId);

    boolean complete(
            String topupId,
            String eventId,
            String ledgerTransactionId,
            String actorId,
            String reason,
            String evidenceReference,
            Instant decidedAt
    );
}
