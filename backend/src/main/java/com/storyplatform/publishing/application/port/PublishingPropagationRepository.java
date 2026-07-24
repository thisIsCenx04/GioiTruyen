package com.storyplatform.publishing.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PublishingPropagationRepository {

    Optional<EdgeTask> claimEdge(
            String workerId,
            Instant now,
            Instant leaseUntil
    );

    boolean complete(
            EdgeTask task,
            String workerId,
            Instant completedAt
    );

    boolean fail(
            EdgeTask task,
            String workerId,
            Instant failedAt,
            Instant availableAt,
            String errorCode,
            int maximumAttempts
    );

    record EdgeTask(
            String id,
            String eventId,
            List<String> targets,
            int attempts
    ) {
        public EdgeTask {
            targets = List.copyOf(targets);
        }
    }
}
