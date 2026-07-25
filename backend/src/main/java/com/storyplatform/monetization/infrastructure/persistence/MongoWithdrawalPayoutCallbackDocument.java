package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.application.port
        .WithdrawalPayoutCallbackRepository;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoWithdrawalPayoutCallbackDocument.COLLECTION)
public record MongoWithdrawalPayoutCallbackDocument(
        @Id String id,
        String provider,
        String eventId,
        String withdrawalId,
        String requestHash,
        Instant receivedAt
) {
    public static final String COLLECTION =
            "withdrawal_payout_callbacks";

    static MongoWithdrawalPayoutCallbackDocument from(
            WithdrawalPayoutCallbackRepository.Receipt value
    ) {
        return new MongoWithdrawalPayoutCallbackDocument(
                value.id(),
                value.provider(),
                value.eventId(),
                value.withdrawalId(),
                value.requestHash(),
                value.receivedAt()
        );
    }

    WithdrawalPayoutCallbackRepository.Receipt toDomain() {
        return new WithdrawalPayoutCallbackRepository.Receipt(
                id,
                provider,
                eventId,
                withdrawalId,
                requestHash,
                receivedAt
        );
    }
}
