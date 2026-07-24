package com.storyplatform.reading.application.port;

import java.time.Instant;

public interface ReadingSessionRepository {

    boolean chapterIsPublished(String storyId, String chapterId);

    void create(SessionRecord session);

    record SessionRecord(
            String id,
            String storyId,
            String chapterId,
            String actorType,
            String actorRef,
            Instant startedAt,
            Instant expiresAt,
            Instant purgeAt
    ) {
    }
}
