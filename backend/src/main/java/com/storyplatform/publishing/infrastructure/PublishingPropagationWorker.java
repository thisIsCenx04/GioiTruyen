package com.storyplatform.publishing.infrastructure;

import com.storyplatform.publishing.application
        .PublishingPropagationOperations;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;
import java.util.UUID;

public final class PublishingPropagationWorker {

    private static final int BATCH_SIZE = 16;
    private final PublishingPropagationOperations operations;
    private final String workerId = UUID.randomUUID().toString();

    public PublishingPropagationWorker(
            PublishingPropagationOperations operations
    ) {
        this.operations = Objects.requireNonNull(operations);
    }

    @Scheduled(
            fixedDelayString =
                    "${app.publishing.propagation.poll-interval:2s}"
    )
    public void poll() {
        for (int item = 0; item < BATCH_SIZE; item++) {
            if (!operations.processNext(workerId)) {
                return;
            }
        }
    }
}
