package com.storyplatform.monetization.application;

import java.time.Instant;
import java.util.List;

public interface WithdrawalOperations {

    Receipt create(
            String actorId,
            String teamId,
            String idempotencyKey,
            long grossAmountXu,
            String destinationId
    );

    Page list(
            String actorId,
            String teamId,
            String cursor,
            int limit
    );

    record Receipt(
            String id,
            String teamId,
            long grossAmountXu,
            String destinationMasked,
            String state,
            boolean replayed,
            Instant createdAt
    ) {
    }

    record Page(List<Receipt> items, String nextCursor) {
        public Page {
            items = List.copyOf(items);
        }
    }
}
