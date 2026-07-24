package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.domain.Donation;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoDonationDocument.COLLECTION)
public record MongoDonationDocument(
        @Id String id,
        String donorId,
        String teamId,
        String donorAccountId,
        String teamAccountId,
        long amountXu,
        String message,
        String ledgerTransactionId,
        String idempotencyKeyHash,
        String requestHash,
        String status,
        Instant createdAt
) {
    public static final String COLLECTION = "donations";

    static MongoDonationDocument from(Donation value) {
        return new MongoDonationDocument(
                value.id(),
                value.donorId(),
                value.teamId(),
                value.donorAccountId(),
                value.teamAccountId(),
                value.amountXu(),
                value.message(),
                value.ledgerTransactionId(),
                value.idempotencyKeyHash(),
                value.requestHash(),
                value.status().name(),
                value.createdAt()
        );
    }

    Donation toDomain() {
        return new Donation(
                id,
                donorId,
                teamId,
                donorAccountId,
                teamAccountId,
                amountXu,
                message,
                ledgerTransactionId,
                idempotencyKeyHash,
                requestHash,
                Donation.Status.valueOf(status),
                createdAt
        );
    }
}
