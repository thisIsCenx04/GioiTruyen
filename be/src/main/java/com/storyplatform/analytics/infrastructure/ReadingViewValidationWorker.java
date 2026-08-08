package com.storyplatform.analytics.infrastructure;

import com.storyplatform.analytics.application.ReadingViewValidationOperations;

import java.util.Objects;
import java.util.UUID;

/**
 * Polling worker that drains the reading-view validation queue in bounded
 * batches of up to 20 items per {@link #poll()} call.
 */
public final class ReadingViewValidationWorker {

    private static final int BATCH_LIMIT = 20;

    private final ReadingViewValidationOperations validation;

    public ReadingViewValidationWorker(ReadingViewValidationOperations validation) {
        this.validation = Objects.requireNonNull(validation, "validation");
    }

    public void poll() {
        String workerId = UUID.randomUUID().toString();
        for (int i = 0; i < BATCH_LIMIT; i++) {
            if (!validation.processNext(workerId)) {
                return;
            }
        }
    }
}
