package com.storyplatform.publishing.infrastructure.persistence;

import com.storyplatform.publishing.domain.PublishingReview;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document(collection = MongoPublishingReviewDocument.COLLECTION)
public record MongoPublishingReviewDocument(
        @Id String id,
        PublishingReview.TargetType targetType,
        String targetId,
        String teamId,
        String submittedRevision,
        long submittedVersion,
        List<PublishingReview.ChapterRevisionRef> chapterRevisions,
        PublishingReview.State state,
        List<Map<String, Object>> checks,
        String assigneeId,
        Map<String, Object> decision,
        String submittedBy,
        Instant submittedAt,
        Instant createdAt,
        Instant updatedAt,
        long version,
        String idempotencyKey,
        String idempotencyFingerprint
) {
    public static final String COLLECTION = "moderation_reviews";

    public static MongoPublishingReviewDocument from(
            PublishingReview review
    ) {
        return new MongoPublishingReviewDocument(
                review.id(),
                review.targetType(),
                review.targetId(),
                review.teamId(),
                review.submittedRevision(),
                review.submittedVersion(),
                review.chapterRevisions(),
                review.state(),
                List.of(),
                null,
                null,
                review.submittedBy(),
                review.submittedAt(),
                review.submittedAt(),
                review.submittedAt(),
                review.version(),
                review.idempotencyKey(),
                review.idempotencyFingerprint()
        );
    }

    public PublishingReview toDomain() {
        return new PublishingReview(
                id,
                targetType,
                targetId,
                teamId,
                submittedRevision,
                submittedVersion,
                chapterRevisions,
                state,
                submittedBy,
                submittedAt,
                version,
                idempotencyKey,
                idempotencyFingerprint
        );
    }
}
