package com.storyplatform.reading.application.contract;

import java.time.Instant;
import java.util.List;

public final class ReadingSessionEvents {

    public static final String HEARTBEAT_ACCEPTED =
            "reading.session.heartbeat";
    public static final String COMPLETED = "reading.session.completed";

    private ReadingSessionEvents() {
    }

    public record HeartbeatBatchAccepted(
            String sessionId,
            String storyId,
            String chapterId,
            String batchId,
            List<Heartbeat> heartbeats
    ) {
        public HeartbeatBatchAccepted {
            heartbeats = List.copyOf(heartbeats);
        }
    }

    public record Heartbeat(
            long sequence,
            Instant occurredAt,
            double position,
            int activeSeconds
    ) {
    }

    public record ReadingSessionCompleted(
            String sessionId,
            String storyId,
            String chapterId,
            String completionId,
            long finalSequence,
            Instant occurredAt,
            double position
    ) {
    }
}
