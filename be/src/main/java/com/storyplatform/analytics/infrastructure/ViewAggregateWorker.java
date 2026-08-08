package com.storyplatform.analytics.infrastructure;

import com.storyplatform.analytics.application.ViewAggregateOperations;
import com.storyplatform.analytics.application.port.ViewAggregateRepository;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Polling worker that drains the view-aggregate processing queue in bounded
 * batches of up to 100 items per {@link #poll()} call.
 */
public final class ViewAggregateWorker {

    private static final int BATCH_LIMIT = 100;

    private final ViewAggregateOperations operations;

    public ViewAggregateWorker(ViewAggregateOperations operations) {
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    public void poll() {
        String workerId = UUID.randomUUID().toString();
        for (int i = 0; i < BATCH_LIMIT; i++) {
            if (!operations.processNext(workerId)) {
                return;
            }
        }
    }
}
