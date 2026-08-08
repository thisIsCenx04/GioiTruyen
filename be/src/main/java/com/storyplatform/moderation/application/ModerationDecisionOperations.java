package com.storyplatform.moderation.application;

import java.time.Instant;
import java.util.List;

public interface ModerationDecisionOperations {

    DecisionView decide(
            String reviewerId,
            String reviewId,
            long expectedVersion,
            DecisionCommand command
    );

    record DecisionCommand(
            Decision decision,
            String reasonCode,
            String note,
            List<String> evidenceRefs,
            String policyVersion
    ) {
        public DecisionCommand {
            evidenceRefs = evidenceRefs == null
                    ? List.of()
                    : List.copyOf(evidenceRefs);
        }
    }

    record DecisionView(
            String reviewId,
            String state,
            Decision decision,
            String reasonCode,
            String policyVersion,
            String reviewerId,
            Instant decidedAt,
            long version
    ) {
    }

    enum Decision {
        APPROVE,
        REQUEST_CHANGES,
        REJECT
    }
}
