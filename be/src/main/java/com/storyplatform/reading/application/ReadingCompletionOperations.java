package com.storyplatform.reading.application;

import java.time.Instant;

public interface ReadingCompletionOperations {

    CompletionReceipt complete(
            String sessionId,
            String sessionToken,
            CompletionCommand command
    );

    record CompletionCommand(
            String completionId,
            long finalSequence,
            Instant occurredAt,
            double position
    ) {
    }

    record CompletionReceipt(
            String completionId,
            String status,
            boolean duplicate
    ) {
    }
}
