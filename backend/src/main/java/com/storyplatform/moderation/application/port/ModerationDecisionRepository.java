package com.storyplatform.moderation.application.port;

import com.storyplatform.moderation.application
        .ModerationDecisionOperations;

import java.time.Instant;
import java.util.List;

public interface ModerationDecisionRepository {

    Result decide(
            String reviewId,
            String reviewerId,
            long expectedVersion,
            DecisionRecord decision,
            Instant now
    );

    record DecisionRecord(
            ModerationDecisionOperations.Decision decision,
            String reasonCode,
            String note,
            List<String> evidenceRefs,
            String policyVersion
    ) {
        public DecisionRecord {
            evidenceRefs = List.copyOf(evidenceRefs);
        }
    }

    record Result(
            Outcome outcome,
            String state,
            long version
    ) {
    }

    enum Outcome {
        SUCCESS,
        CONFLICT,
        STALE_TARGET
    }
}
