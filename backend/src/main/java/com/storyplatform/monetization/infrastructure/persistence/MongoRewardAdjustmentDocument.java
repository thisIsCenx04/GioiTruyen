package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.domain.RewardAdjustment;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoRewardAdjustmentDocument.COLLECTION)
public record MongoRewardAdjustmentDocument(
        @Id String id,
        String settlementId,
        long correctedValidViews,
        long previousAmountXu,
        long correctedAmountXu,
        long deltaXu,
        String reasonCode,
        String idempotencyKeyHash,
        String ledgerTransactionId,
        Instant createdAt
) {
    public static final String COLLECTION = "reward_adjustments";

    static MongoRewardAdjustmentDocument from(RewardAdjustment value) {
        return new MongoRewardAdjustmentDocument(
                value.id(), value.settlementId(),
                value.correctedValidViews(), value.previousAmountXu(),
                value.correctedAmountXu(), value.deltaXu(),
                value.reasonCode(), value.idempotencyKeyHash(),
                value.ledgerTransactionId(), value.createdAt()
        );
    }

    RewardAdjustment toDomain() {
        return new RewardAdjustment(
                id, settlementId, correctedValidViews, previousAmountXu,
                correctedAmountXu, deltaXu, reasonCode,
                idempotencyKeyHash, ledgerTransactionId, createdAt
        );
    }
}
