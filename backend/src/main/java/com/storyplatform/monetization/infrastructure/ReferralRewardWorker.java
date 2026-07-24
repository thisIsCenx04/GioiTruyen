package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.ReferralOperations;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;

public final class ReferralRewardWorker {

    private final ReferralOperations referrals;

    public ReferralRewardWorker(ReferralOperations referrals) {
        this.referrals = Objects.requireNonNull(referrals);
    }

    @Scheduled(
            fixedDelayString =
                    "${app.monetization.referrals.poll-interval:5m}"
    )
    public void rewardEligible() {
        referrals.rewardEligible(100);
    }
}
