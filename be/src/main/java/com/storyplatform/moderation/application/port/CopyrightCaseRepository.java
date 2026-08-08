package com.storyplatform.moderation.application.port;

import com.storyplatform.moderation.application.CopyrightCaseOperations;

import java.util.List;

public interface CopyrightCaseRepository {

    boolean storyIsPublic(String storyId);

    boolean evidenceIsPrivateAndOwned(
            String claimantId,
            List<String> evidenceMediaIds
    );

    CreateResult createAndHold(
            CopyrightCaseOperations.CopyrightCaseView copyrightCase
    );

    CreateResult appeal(
            String caseId,
            String actorId,
            String statement,
            java.time.Instant appealedAt
    );

    CreateResult decide(
            String caseId,
            String reviewerId,
            CopyrightCaseOperations.Decision decision,
            String reasonCode,
            String note,
            java.time.Instant decidedAt
    );

    record CreateResult(
            Outcome outcome,
            CopyrightCaseOperations.CopyrightCaseView copyrightCase
    ) {
    }

    enum Outcome {
        SUCCESS,
        DUPLICATE,
        STORY_CHANGED
    }
}
