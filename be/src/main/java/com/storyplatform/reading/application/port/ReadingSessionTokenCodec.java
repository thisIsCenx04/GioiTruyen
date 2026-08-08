package com.storyplatform.reading.application.port;

import java.time.Instant;

public interface ReadingSessionTokenCodec {

    String fingerprint(String subject);

    String issue(Claims claims);

    Claims verify(String token, Instant now);

    record Claims(
            String sessionId,
            String storyId,
            String chapterId,
            String actorRef,
            Instant issuedAt,
            Instant expiresAt
    ) {
    }
}
