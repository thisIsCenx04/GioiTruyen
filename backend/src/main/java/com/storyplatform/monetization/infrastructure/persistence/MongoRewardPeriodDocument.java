package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.domain.RewardPeriod;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

@Document(collection = MongoRewardPeriodDocument.COLLECTION)
public record MongoRewardPeriodDocument(
        @Id String id,
        LocalDate periodDate,
        Instant periodStart,
        Instant periodEnd,
        String ruleVersion,
        long xuPerThousandValidViews,
        long teamCapXu,
        String aggregateVersion,
        long teamCount,
        long validViews,
        String state,
        Instant lockedAt,
        Instant settledAt
) {
    public static final String COLLECTION = "reward_periods";

    static MongoRewardPeriodDocument from(RewardPeriod value) {
        return new MongoRewardPeriodDocument(
                value.id(), value.periodDate(), value.periodStart(),
                value.periodEnd(), value.ruleVersion(),
                value.xuPerThousandValidViews(), value.teamCapXu(),
                value.aggregateVersion(), value.teamCount(),
                value.validViews(), value.state().name(),
                value.lockedAt(), value.settledAt()
        );
    }

    RewardPeriod toDomain() {
        return new RewardPeriod(
                id, periodDate, periodStart, periodEnd, ruleVersion,
                xuPerThousandValidViews, teamCapXu, aggregateVersion,
                teamCount, validViews, RewardPeriod.State.valueOf(state),
                lockedAt, settledAt
        );
    }
}
