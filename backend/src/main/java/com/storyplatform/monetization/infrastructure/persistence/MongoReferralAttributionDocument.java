package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.domain.ReferralAttribution;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = MongoReferralAttributionDocument.COLLECTION)
public record MongoReferralAttributionDocument(
        @Id String id,
        String referrerId,
        String refereeId,
        String referralCodeHash,
        String ruleVersion,
        long rewardXu,
        String state,
        List<String> riskSignals,
        String ledgerTransactionId,
        Instant attributedAt,
        Instant eligibleAt,
        Instant rewardedAt,
        String idempotencyKeyHash,
        String requestHash
) {
    public static final String COLLECTION = "referral_attributions";

    static MongoReferralAttributionDocument from(
            ReferralAttribution value,
            String keyHash,
            String requestHash
    ) {
        return new MongoReferralAttributionDocument(
                value.id(), value.referrerId(), value.refereeId(),
                value.referralCodeHash(), value.ruleVersion(),
                value.rewardXu(), value.state().name(),
                value.riskSignals().stream().map(Enum::name).toList(),
                value.ledgerTransactionId(), value.attributedAt(),
                value.eligibleAt(), value.rewardedAt(),
                keyHash, requestHash
        );
    }

    ReferralAttribution toDomain() {
        return new ReferralAttribution(
                id, referrerId, refereeId, referralCodeHash,
                ruleVersion, rewardXu,
                ReferralAttribution.State.valueOf(state),
                riskSignals.stream()
                        .map(ReferralAttribution.RiskSignal::valueOf)
                        .toList(),
                ledgerTransactionId, attributedAt, eligibleAt, rewardedAt
        );
    }
}
