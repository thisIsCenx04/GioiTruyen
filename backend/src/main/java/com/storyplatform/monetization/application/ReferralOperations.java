package com.storyplatform.monetization.application;

import java.time.Instant;

public interface ReferralOperations {

    AttributionView attribute(
            String refereeId,
            String code,
            String idempotencyKey
    );

    ReferralView mine(String userId);

    int rewardEligible(int limit);

    record AttributionView(
            String id,
            String state,
            Instant attributedAt,
            Instant eligibleAt,
            boolean replayed
    ) {
    }

    record ReferralView(
            String code,
            String attributedBy,
            String attributionState,
            long referredCount,
            long pendingCount,
            long rewardedCount,
            long rewardedXu
    ) {
    }
}
