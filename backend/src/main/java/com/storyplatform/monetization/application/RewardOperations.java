package com.storyplatform.monetization.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface RewardOperations {

    PeriodView settle(LocalDate periodDate);

    AdjustmentView adjust(
            String settlementId,
            long correctedValidViews,
            String reasonCode,
            String idempotencyKey
    );

    List<SettlementView> recent(
            String actorId,
            String teamId,
            int limit
    );

    record PeriodView(
            String periodId,
            String state,
            String ruleVersion,
            String aggregateVersion,
            long teamCount,
            long validViews,
            long rewardedXu,
            Instant lockedAt,
            Instant settledAt,
            boolean replayed
    ) {
    }

    record SettlementView(
            String id,
            String periodId,
            long validViews,
            long amountXu,
            boolean capApplied,
            String ruleVersion,
            String aggregateVersion,
            String state,
            Instant createdAt,
            Instant postedAt
    ) {
    }

    record AdjustmentView(
            String id,
            String settlementId,
            long correctedValidViews,
            long previousAmountXu,
            long correctedAmountXu,
            long deltaXu,
            String reasonCode,
            String ledgerTransactionId,
            Instant createdAt,
            boolean replayed
    ) {
    }
}
