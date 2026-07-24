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
        long submittedVersion,
        List<FrozenChapterRevision> chapterRevisions,
        String state,
        List<ModerationQueueOperations.CheckSummary> checks,
        boolean manualFallback,
        int priority,
        String assigneeId,
        Instant leaseUntil,
        Instant submittedAt,
        Instant updatedAt,
        long version,
        ModerationDecisionDocument decision
) {
    public static final String COLLECTION = "moderation_reviews";

    public record FrozenChapterRevision(
            String chapterId,
            String revisionId,
            int number
    ) {
    }

    public record ModerationDecisionDocument(
            String decision,
            String reasonCode,
            String note,
            List<String> evidenceRefs,
            String policyVersion,
            String reviewerId,
            Instant decidedAt
    ) {
        public ModerationDecisionDocument {
            evidenceRefs = List.copyOf(evidenceRefs);
        }
    }
}
