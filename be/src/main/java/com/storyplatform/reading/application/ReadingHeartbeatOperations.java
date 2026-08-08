package com.storyplatform.reading.application;

import java.time.Instant;
import java.util.List;

public interface ReadingHeartbeatOperations {

    HeartbeatReceipt ingest(
            String sessionId,
            String sessionToken,
            HeartbeatBatch batch
    );

    record HeartbeatBatch(
            String batchId,
            List<Heartbeat> heartbeats
    ) {
        public HeartbeatBatch {
            heartbeats = List.copyOf(heartbeats);
        }
    }

    record Heartbeat(
            long sequence,
            Instant occurredAt,
            double position,
            int activeSeconds
    ) {
    }

    record HeartbeatReceipt(
            String batchId,
            long nextSequence,
            boolean duplicate
    ) {
    }
}
