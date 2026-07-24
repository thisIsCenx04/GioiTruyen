package com.storyplatform.reading.application;

import java.time.Instant;

public interface ReadingSessionOperations {

    SessionGrant start(StartCommand command);

    record StartCommand(
            String storyId,
            String chapterId,
            String anonymousId,
            String authenticatedUserId,
            String clientAddress
    ) {
    }

    record SessionGrant(
            String sessionId,
            String sessionToken,
            Instant expiresAt,
            int heartbeatIntervalSeconds
    ) {
    }
}
