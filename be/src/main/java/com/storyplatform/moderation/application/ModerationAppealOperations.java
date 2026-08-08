package com.storyplatform.moderation.application;

import java.time.Instant;

public interface ModerationAppealOperations {

    AppealView create(String actorId, String reviewId, String statement);

    AppealView decide(
            String reviewerId,
            String reviewId,
            String appealId,
            AppealDecision decision,
            String reasonCode,
            String note
    );

    record AppealView(
            String id,
            String reviewId,
            String appellantId,
            String originalReviewerId,
            String statement,
            String status,
            AppealDecision decision,
            String decisionReasonCode,
            String decisionNote,
            String appealReviewerId,
            Instant createdAt,
            Instant deadline,
            Instant decidedAt
    ) {
    }

    enum AppealDecision {
        UPHOLD,
        OVERTURN
    }
}
