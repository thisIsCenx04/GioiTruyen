package com.storyplatform.moderation.application.port;

import com.storyplatform.moderation.application.ModerationAppealOperations;

import java.time.Instant;
import java.util.Optional;

public interface ModerationAppealRepository {

    Optional<EligibleReview> eligibleReview(String reviewId, String actorId);

    CreateResult createIfAbsent(
            String appealId,
            EligibleReview review,
            String actorId,
            String statement,
            Instant createdAt,
            Instant deadline
    );

    ResolveResult resolve(
            String reviewId,
            String appealId,
            String reviewerId,
            ModerationAppealOperations.AppealDecision decision,
            String reasonCode,
            String note,
            Instant decidedAt
    );

    record EligibleReview(
            String reviewId,
            String originalReviewerId,
            Instant decidedAt
    ) {
    }

    record CreateResult(
            Outcome outcome,
            ModerationAppealOperations.AppealView appeal
    ) {
    }

    record ResolveResult(
            Outcome outcome,
            ModerationAppealOperations.AppealView appeal
    ) {
    }

    enum Outcome {
        SUCCESS,
        DUPLICATE,
        CONFLICT
    }
}
