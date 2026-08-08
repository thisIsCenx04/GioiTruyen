package com.storyplatform.reading.application.port;

import java.time.Instant;

public interface ReadingCompletionRepository {

    CompleteResult complete(
            String sessionId,
            String actorRef,
            String completionId,
            long finalSequence,
            Instant completedAt,
            Instant now
    );

    enum CompleteResult {
        APPLIED,
        DUPLICATE,
        SEQUENCE_CONFLICT,
        NOT_ACTIVE
    }
}
