package com.storyplatform.monetization.application.port;

import com.storyplatform.monetization.domain.RewardPeriod;
import com.storyplatform.monetization.domain.RewardAdjustment;
import com.storyplatform.monetization.domain.RewardSettlement;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RewardRepository {

    Optional<RewardPeriod> findPeriod(String periodId);

    LockResult lock(
            RewardPeriod period,
            List<RewardSettlement> settlements
    );

    List<RewardSettlement> findByPeriod(String periodId);

    List<RewardSettlement> findRecentByTeam(String teamId, int limit);

    Optional<RewardSettlement> findSettlement(String settlementId);

    Optional<RewardAdjustment> findAdjustmentByKeyHash(String keyHash);

    Optional<RewardAdjustment> findAdjustmentBySettlement(
            String settlementId
    );

    RewardAdjustment insertAdjustment(RewardAdjustment adjustment);

    boolean markNoReward(String settlementId, Instant at);

    boolean markPosted(
            String settlementId,
            String ledgerTransactionId,
            Instant at
    );

    boolean markPeriodSettled(String periodId, Instant at);

    record LockResult(RewardPeriod period, boolean created) {
    }
}
