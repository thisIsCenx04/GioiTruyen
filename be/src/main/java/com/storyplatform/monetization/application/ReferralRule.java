package com.storyplatform.monetization.application;

import java.time.Duration;

public record ReferralRule(
        String version,
        long rewardXu,
        Duration attributionWindow,
        Duration referrerMinimumAge,
        Duration rewardDelay,
        int maximumRecentReferrals
) {
    public ReferralRule {
        if (version == null
                || !version.matches("[a-z0-9][a-z0-9._-]{2,63}")
                || rewardXu < 1
                || !positive(attributionWindow)
                || !positive(referrerMinimumAge)
                || !positive(rewardDelay)
                || maximumRecentReferrals < 1
                || maximumRecentReferrals > 10_000) {
            throw new IllegalArgumentException("Referral rule is invalid.");
        }
    }

    private static boolean positive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }
}
