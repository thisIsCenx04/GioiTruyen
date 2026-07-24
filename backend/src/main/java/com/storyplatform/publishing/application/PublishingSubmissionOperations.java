package com.storyplatform.publishing.application;

import java.time.Instant;
import java.util.List;

public interface PublishingSubmissionOperations {

    SubmissionView submit(
            String actorId,
            String teamId,
            String storyId,
            String idempotencyKey
    );

    record SubmissionView(
            String reviewId,
            String storyId,
            String submittedRevision,
            List<FrozenChapterRevision> chapterRevisions,
            String state,
            long version,
            Instant submittedAt
    ) {
        public SubmissionView {
            chapterRevisions = List.copyOf(chapterRevisions);
        }
    }

    record FrozenChapterRevision(
            String chapterId,
            String revisionId,
            int number
    ) {
    }
}
