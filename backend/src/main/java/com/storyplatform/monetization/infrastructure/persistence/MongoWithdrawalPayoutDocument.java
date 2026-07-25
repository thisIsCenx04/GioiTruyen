package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.domain.WithdrawalPayout;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoWithdrawalPayoutDocument.COLLECTION)
public record MongoWithdrawalPayoutDocument(
        @Id String withdrawalId,
        String provider,
        String providerIdempotencyKey,
        String state,
        int attempt,
        String providerReference,
        String lastErrorCode,
        Instant nextAttemptAt,
        Instant leaseUntil,
        Instant startedAt,
        Instant completedAt,
        String settlementTransactionId,
        String releaseTransactionId
) {
    public static final String COLLECTION = "withdrawal_payouts";

    static MongoWithdrawalPayoutDocument from(
            WithdrawalPayout value
    ) {
        return new MongoWithdrawalPayoutDocument(
                value.withdrawalId(),
                value.provider(),
                value.providerIdempotencyKey(),
                value.state().name(),
                value.attempt(),
                value.providerReference(),
                value.lastErrorCode(),
                value.nextAttemptAt(),
                value.leaseUntil(),
                value.startedAt(),
                value.completedAt(),
                value.settlementTransactionId(),
                value.releaseTransactionId()
        );
    }

    WithdrawalPayout toDomain() {
        return new WithdrawalPayout(
                withdrawalId,
                provider,
                providerIdempotencyKey,
                WithdrawalPayout.State.valueOf(state),
                attempt,
                providerReference,
                lastErrorCode,
                nextAttemptAt,
                leaseUntil,
                startedAt,
                completedAt,
                settlementTransactionId,
                releaseTransactionId
        );
    }
}
