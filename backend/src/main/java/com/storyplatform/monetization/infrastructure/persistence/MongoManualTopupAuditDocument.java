package com.storyplatform.monetization.infrastructure.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoManualTopupAuditDocument.COLLECTION)
public record MongoManualTopupAuditDocument(
        @Id String id,
        String action,
        String topupRequestId,
        String paymentEventId,
        String ledgerTransactionId,
        String actorId,
        String reason,
        String evidenceReference,
        Instant decidedAt
) {
    public static final String COLLECTION = "audit_logs";
}
