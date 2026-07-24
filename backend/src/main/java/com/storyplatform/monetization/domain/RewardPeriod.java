package com.storyplatform.monetization.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.regex.Pattern;

public record RewardPeriod(
        String id,
        LocalDate periodDate,
        Instant periodStart,
        Instant periodEnd,
        String ruleVersion,
        long xuPerThousandValidViews,
        long teamCapXu,
        String aggregateVersion,
        long teamCount,
        long validViews,
        State state,
        Instant lockedAt,
        Instant settledAt
) {
    private static final Pattern VERSION =
            Pattern.compile("[a-z0-9][a-z0-9._-]{2,63}");

    public RewardPeriod {
        if (id == null || !id.equals(periodDate.toString())
                || periodStart == null || periodEnd == null
                || !periodStart.isBefore(periodEnd)
                || !VERSION.matcher(ruleVersion).matches()
                || !VERSION.matcher(aggregateVersion).matches()
                || xuPerThousandValidViews < 1
                || teamCapXu < 1 || teamCount < 0 || validViews < 0
                || state == null || lockedAt == null
                || (state == State.SETTLED) != (settledAt != null)) {
            throw new IllegalArgumentException(
                    "Reward period is invalid."
            );
        }
    }

    public RewardPeriod settle(Instant at) {
        if (state != State.LOCKED || at == null || at.isBefore(lockedAt)) {
            throw new IllegalStateException(
                    "Only a locked reward period can be settled."
            );
        }
        return new RewardPeriod(
                id,
                periodDate,
                periodStart,
                periodEnd,
                ruleVersion,
                xuPerThousandValidViews,
                teamCapXu,
                aggregateVersion,
                teamCount,
                validViews,
                State.SETTLED,
                lockedAt,
                at
        );
    }

    public enum State {
        LOCKED,
        SETTLED
    }
}
