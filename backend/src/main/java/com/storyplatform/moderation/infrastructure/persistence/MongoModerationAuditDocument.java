package com.storyplatform.moderation.infrastructure.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = MongoModerationAuditDocument.COLLECTION)
public record MongoModerationAuditDocument(
        @Id String id,
        String eventType,
        String actorId,
        String targetType,
        String targetId,
        String teamId,
        String action,
        String reasonCode,
        String policyVersion,
        List<String> evidenceRefs,
        Instant createdAt
) {
    public static final String COLLECTION = "audit_logs";

    public MongoModerationAuditDocument {
        evidenceRefs = List.copyOf(evidenceRefs);
    }
}
