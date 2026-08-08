package com.storyplatform.monetization.application;

import java.time.Instant;

public interface WithdrawalReviewOperations {

    Decision approve(
            String actorId,
            String withdrawalId,
            String reauthenticationToken,
            String idempotencyKey,
            String reason
    );

    Decision reject(
            String actorId,
            String withdrawalId,
            String reauthenticationToken,
            String idempotencyKey,
            String reason
    );

    record Decision(
            String withdrawalId,
            String state,
            String reviewerId,
            String reason,
            String riskLevel,
            String riskRuleVersion,
            String releaseTransactionId,
            boolean replayed,
            Instant reviewedAt
    ) {
    }
}
