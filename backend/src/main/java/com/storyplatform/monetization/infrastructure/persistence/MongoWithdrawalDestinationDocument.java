package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.domain.Withdrawal;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoWithdrawalDestinationDocument.COLLECTION)
public record MongoWithdrawalDestinationDocument(
        @Id String id,
        String teamId,
        String maskedLabel,
        String encryptedPayload,
        long version,
        String state,
        Instant verifiedAt,
        Instant availableAt
) {
    public static final String COLLECTION = "withdrawal_destinations";

    Withdrawal.DestinationSnapshot snapshot() {
        return new Withdrawal.DestinationSnapshot(
                id,
                maskedLabel,
                encryptedPayload,
                version,
                verifiedAt
        );
    }
}
