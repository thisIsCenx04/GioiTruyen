package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.domain.Withdrawal;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoWithdrawalDocument.COLLECTION)
public record MongoWithdrawalDocument(
        @Id String id,
        String teamId,
        String accountId,
        long grossAmountXu,
        long feeXu,
        long netAmountXu,
        String feeRuleVersion,
        DestinationSnapshot destinationSnapshot,
        String state,
        String requestedBy,
        String reserveTransactionId,
        String idempotencyKeyHash,
        String requestHash,
        Instant createdAt
) {
    public static final String COLLECTION = "withdrawals";

    static MongoWithdrawalDocument from(Withdrawal value) {
        return new MongoWithdrawalDocument(
                value.id(),
                value.teamId(),
                value.accountId(),
                value.grossAmountXu(),
                value.feeXu(),
                value.netAmountXu(),
                value.feeRuleVersion(),
                DestinationSnapshot.from(value.destination()),
                value.state().name(),
                value.requestedBy(),
                value.reserveTransactionId(),
                value.idempotencyKeyHash(),
                value.requestHash(),
                value.createdAt()
        );
    }

    Withdrawal toDomain() {
        return new Withdrawal(
                id,
                teamId,
                accountId,
                grossAmountXu,
                feeXu,
                netAmountXu,
                feeRuleVersion,
                destinationSnapshot.toDomain(),
                Withdrawal.State.valueOf(state),
                requestedBy,
                reserveTransactionId,
                idempotencyKeyHash,
                requestHash,
                createdAt
        );
    }

    public record DestinationSnapshot(
            String id,
            String maskedLabel,
            String encryptedPayload,
            long version,
            Instant verifiedAt
    ) {
        static DestinationSnapshot from(
                Withdrawal.DestinationSnapshot value
        ) {
            return new DestinationSnapshot(
                    value.id(),
                    value.maskedLabel(),
                    value.encryptedPayload(),
                    value.version(),
                    value.verifiedAt()
            );
        }

        Withdrawal.DestinationSnapshot toDomain() {
            return new Withdrawal.DestinationSnapshot(
                    id,
                    maskedLabel,
                    encryptedPayload,
                    version,
                    verifiedAt
            );
        }
    }
}
