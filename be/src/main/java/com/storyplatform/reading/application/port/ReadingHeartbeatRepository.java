package com.storyplatform.reading.application.port;

import java.time.Instant;

public interface ReadingHeartbeatRepository {

    ApplyResult apply(
            String sessionId,
            String actorRef,
            String batchId,
            long expectedPreviousSequence,
            long lastSequence,
            Instant now
    );

    enum ApplyResult {
        APPLIED,
        DUPLICATE,
        SEQUENCE_CONFLICT,
        NOT_ACTIVE
    }
}
