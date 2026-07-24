package com.storyplatform.moderation.infrastructure.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoModerationAppealDocument.COLLECTION)
public record MongoModerationAppealDocument(
        @Id String id,
        String reviewId,
        String appellantId,
        String originalReviewerId,
        String statement,
        String status,
        String decision,
        String decisionReasonCode,
        String decisionNote,
        String appealReviewerId,
        Instant createdAt,
        Instant deadline,
        Instant decidedAt
) {
    public static final String COLLECTION = "moderation_appeals";
}
