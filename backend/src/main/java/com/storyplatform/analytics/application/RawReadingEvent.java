package com.storyplatform.analytics.application;

import java.time.Instant;
import java.util.Objects;

public record RawReadingEvent(
        String eventId,
        Kind kind,
        String sessionRef,
        String storyId,
        String chapterId,
        long sequence,
        Instant occurredAt,
        double position,
        Integer activeSeconds,
        Instant receivedAt
) {
    public RawReadingEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(sessionRef, "sessionRef");
        Objects.requireNonNull(storyId, "storyId");
        Objects.requireNonNull(chapterId, "chapterId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }

    public enum Kind {
        HEARTBEAT,
        COMPLETION
    }
}
