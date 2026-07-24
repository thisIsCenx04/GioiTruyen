package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.domain.RewardSettlement;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoRewardSettlementDocument.COLLECTION)
public record MongoRewardSettlementDocument(
        @Id String id,
        String periodId,
        String teamId,
        String teamAccountId,
        long validViews,
        long amountXu,
        boolean capApplied,
        String ruleVersion,
        String aggregateVersion,
        String ledgerTransactionId,
        String state,
        Instant createdAt,
        Instant postedAt
) {
    public static final String COLLECTION = "reward_settlements";

    static MongoRewardSettlementDocument from(RewardSettlement value) {
        return new MongoRewardSettlementDocument(
                value.id(), value.periodId(), value.teamId(),
                value.teamAccountId(), value.validViews(),
                value.amountXu(), value.capApplied(), value.ruleVersion(),
                value.aggregateVersion(), value.ledgerTransactionId(),
                value.state().name(), value.createdAt(), value.postedAt()
        );
    }

    RewardSettlement toDomain() {
        return new RewardSettlement(
                id, periodId, teamId, teamAccountId, validViews, amountXu,
                capApplied, ruleVersion, aggregateVersion,
                ledgerTransactionId,
                RewardSettlement.State.valueOf(state),
                createdAt, postedAt
        );
    }
}
