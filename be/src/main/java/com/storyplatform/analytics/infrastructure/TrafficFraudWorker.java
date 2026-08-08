package com.storyplatform.analytics.infrastructure;

import com.storyplatform.analytics.application.TrafficFraudOperations;

import java.util.Objects;
import java.util.UUID;

/**
 * Polling worker that drains the traffic-fraud scoring queue in bounded
 * batches of up to 50 items per {@link #poll()} call.
 */
public final class TrafficFraudWorker {

    private static final int BATCH_LIMIT = 50;

    private final TrafficFraudOperations operations;

    public TrafficFraudWorker(TrafficFraudOperations operations) {
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
