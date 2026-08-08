package com.storyplatform.moderation.application;

import java.time.Instant;
import java.util.List;

public interface ModerationQueueOperations {

    ReviewPage list(int limit, String cursor);

    ReviewDetail detail(String reviewId);

    ReviewCase claim(
            String reviewerId,
            String reviewId,
            long expectedVersion
    );

    record ReviewPage(List<ReviewCase> items, String nextCursor) {
        public ReviewPage {
            items = List.copyOf(items);
        }
    }

    record ReviewCase(
            String id,
            String targetType,
            String targetId,
            String teamId,
            String state,
            int priority,
            boolean manualFallback,
            List<CheckSummary> checks,
            String assigneeId,
            Instant leaseUntil,
            Instant submittedAt,
            long version
    ) {
        public ReviewCase {
            checks = List.copyOf(checks);
        }
    }

    record CheckSummary(
            String rule,
            String outcome,
            String code,
            String policyVersion
    ) {
    }

    record ReviewDetail(
            ReviewCase review,
            StoryEvidence story,
            List<ChapterEvidence> chapters
    ) {
        public ReviewDetail {
            chapters = List.copyOf(chapters);
        }
    }

    record StoryEvidence(
            String revisionId,
            long revisionNo,
            String title,
            String synopsis,
            String origin,
            String language,
            List<String> categoryIds,
            String coverAssetId,
            String checksum
    ) {
        public StoryEvidence {
            categoryIds = List.copyOf(categoryIds);
        }
    }

    record ChapterEvidence(
            String chapterId,
            String revisionId,
            int number,
            long revisionNo,
            String contentHtml,
            String plainText,
            String checksum
    ) {
    }
}
