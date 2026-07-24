package com.storyplatform.moderation.application;

import java.time.Instant;
import java.util.List;

public interface CopyrightCaseOperations {

    CopyrightCaseView create(
            String claimantId,
            CreateCopyrightCase command
    );

    CopyrightCaseView appeal(
            String actorId,
            String caseId,
            String statement
    );

    CopyrightCaseView decide(
            String reviewerId,
            String caseId,
            Decision decision,
            String reasonCode,
            String note
    );

    record CreateCopyrightCase(
            String storyId,
            String claimantName,
            String statement,
            List<String> evidenceMediaIds
    ) {
        public CreateCopyrightCase {
            evidenceMediaIds = evidenceMediaIds == null
                    ? List.of()
                    : List.copyOf(evidenceMediaIds);
        }
    }

    record CopyrightCaseView(
            String id,
            String storyId,
            String claimantId,
            String claimantName,
            String statement,
            List<String> evidenceMediaIds,
            String status,
            Instant createdAt,
            Instant responseDueAt,
            Instant holdUntil,
            String appealStatement,
            String appealActorId,
            Instant appealedAt,
            Decision decision,
            String decisionReasonCode,
            String decisionNote,
            String reviewerId,
            Instant decidedAt
    ) {
        public CopyrightCaseView {
            evidenceMediaIds = List.copyOf(evidenceMediaIds);
        }
    }

    enum Decision {
        TAKEDOWN,
        REINSTATE
    }
}
