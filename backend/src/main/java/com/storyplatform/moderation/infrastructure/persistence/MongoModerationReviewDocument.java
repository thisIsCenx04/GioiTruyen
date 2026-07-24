package com.storyplatform.moderation.infrastructure.persistence;

import com.storyplatform.moderation.application.ModerationQueueOperations;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = MongoModerationReviewDocument.COLLECTION)
public record MongoModerationReviewDocument(
        @Id String id,
        String targetType,
        String targetId,
        String teamId,
        String submittedRevision,
        String state,
        List<ModerationQueueOperations.CheckSummary> checks,
        boolean manualFallback,
        int priority,
        String assigneeId,
        Instant leaseUntil,
        Instant submittedAt,
        Instant updatedAt,
        long version
) {
    public static final String COLLECTION = "moderation_reviews";
}
