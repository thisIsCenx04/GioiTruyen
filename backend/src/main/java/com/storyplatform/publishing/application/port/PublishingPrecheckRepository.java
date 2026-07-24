package com.storyplatform.publishing.application.port;

import com.storyplatform.publishing.application.PublishingPrecheckEngine;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PublishingPrecheckRepository {

    Optional<ClaimedReview> claim(
            String workerId,
            Instant now,
            Instant leaseUntil
    );

    Optional<ReviewEvidence> loadEvidence(ClaimedReview review);

    boolean complete(
            ClaimedReview review,
            String workerId,
            List<PublishingPrecheckEngine.CheckResult> checks,
            boolean manualFallback,
            Instant completedAt
    );

    record ClaimedReview(
            String reviewId,
            String teamId,
            String storyId,
            String storyRevision,
            List<FrozenChapter> chapters,
            long version,
            Instant leaseUntil
    ) {
        public ClaimedReview {
            chapters = List.copyOf(chapters);
        }
    }

    record FrozenChapter(
            String chapterId,
            String revisionId,
            int number
    ) {
    }

    record ReviewEvidence(
            ClaimedReview review,
            String storyTitle,
            String storySynopsis,
            String coverAssetId,
            MediaEvidence cover,
            List<ChapterEvidence> chapters
    ) {
        public ReviewEvidence {
            chapters = List.copyOf(chapters);
        }
    }

    record MediaEvidence(
            String ownerType,
            String ownerId,
            String purpose,
            String state,
            String moderationState
    ) {
    }

    record ChapterEvidence(
            String chapterId,
            String contentHtml,
            String plainText,
            String checksum
    ) {
    }
}
