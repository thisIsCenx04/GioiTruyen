package com.storyplatform.monetization.application.port;

import com.storyplatform.monetization.domain.PaymentEvent;
import com.storyplatform.monetization.domain.TopupRequest;

import java.time.Instant;
import java.util.Optional;

public interface TopupRejectionRepository {

    Optional<Review> find(String topupId);

    boolean reject(
            Review review,
            String actorId,
            String reasonCode,
            String reason,
            String evidenceReference,
            Instant decidedAt
    );

    record Review(TopupRequest topup, PaymentEvent paymentEvent) {
    }
}
