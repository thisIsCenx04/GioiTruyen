package com.storyplatform.monetization.application;

import java.time.Instant;

public interface DonationOperations {

    Receipt donate(
            String donorId,
            String idempotencyKey,
            String teamId,
            long amountXu,
            String message
    );

    record Receipt(
            String donationId,
            String teamId,
            long amountXu,
            String message,
            String ledgerTransactionId,
            String status,
            boolean replayed,
            Instant createdAt
    ) {
    }
}
