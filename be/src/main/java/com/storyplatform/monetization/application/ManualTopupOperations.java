package com.storyplatform.monetization.application;

import java.time.Instant;

public interface ManualTopupOperations {

    Approval approve(
            String actorId,
            String topupId,
            String reauthenticationToken,
            String reason,
            String evidenceReference
    );

    record Approval(
            String topupId,
            String paymentEventId,
            String ledgerTransactionId,
            String status,
            Instant decidedAt
    ) {
    }
}
