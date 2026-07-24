package com.storyplatform.monetization.infrastructure.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = MongoMonetizationConfigAuditDocument.COLLECTION)
public record MongoMonetizationConfigAuditDocument(
        @Id String id,
        String module,
        String action,
        String targetType,
        String targetId,
        String actorId,
        BigDecimal beforePercent,
        BigDecimal afterPercent,
        long version,
        String reason,
        Instant createdAt
) {
    public static final String COLLECTION = "audit_logs";
}
