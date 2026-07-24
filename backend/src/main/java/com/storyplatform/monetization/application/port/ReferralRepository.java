package com.storyplatform.monetization.application.port;

import com.storyplatform.monetization.domain.ReferralAttribution;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReferralRepository {

    Optional<ReferralAttribution> findByRefereeId(String refereeId);

    Optional<StoredAttribution> findByIdempotencyKeyHash(String keyHash);

    ReferralAttribution insert(
            ReferralAttribution attribution,
            String idempotencyKeyHash,
            String requestHash
    );

    long countRecentByReferrer(String referrerId, Instant since);

    List<ReferralAttribution> findRewardable(
            Instant now,
            int limit
    );

    boolean markRewarded(
            String attributionId,
            String ledgerTransactionId,
            Instant rewardedAt
    );

    Summary summary(String userId);

    record Summary(
            long referredCount,
            long pendingCount,
            long rewardedCount,
            long rewardedXu
    ) {
    }

    record StoredAttribution(
            ReferralAttribution attribution,
            String requestHash
    ) {
    }
}
