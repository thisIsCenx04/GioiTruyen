package com.storyplatform.publishing.application.port;

import com.storyplatform.publishing.domain.PublishingReview;

import java.util.List;
import java.util.Optional;

public interface PublishingSubmissionRepository {

    Optional<PublishingReview> findReplay(
            String teamId,
            String idempotencyKey
    );

    Optional<SubmissionCandidate> findCandidate(
            String teamId,
            String storyId
    );

    boolean submit(
            SubmissionCandidate candidate,
            PublishingReview review
    );

    record SubmissionCandidate(
            String storyId,
            String teamId,
            String storyRevision,
            long storyVersion,
            List<PublishingReview.ChapterRevisionRef> chapterRevisions
    ) {
        public SubmissionCandidate {
            chapterRevisions = List.copyOf(chapterRevisions);
        }
    }
}
