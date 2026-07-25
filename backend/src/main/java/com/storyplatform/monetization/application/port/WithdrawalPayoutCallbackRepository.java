package com.storyplatform.monetization.application.port;

import java.time.Instant;
import java.util.Optional;

public interface WithdrawalPayoutCallbackRepository {

    Optional<Receipt> find(String provider, String eventId);

    void insert(Receipt receipt);

    record Receipt(
            String id,
            String provider,
            String eventId,
            String withdrawalId,
            String requestHash,
            Instant receivedAt
    ) {
    }
}
