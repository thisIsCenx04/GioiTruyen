package com.storyplatform.analytics.infrastructure;

import com.storyplatform.analytics.application
        .ReadingViewValidationOperations;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;
import java.util.UUID;

public final class ReadingViewValidationWorker {

    private static final int MAXIMUM_BATCH_SIZE = 20;
    private final ReadingViewValidationOperations validation;
    private final String workerId = UUID.randomUUID().toString();

    public ReadingViewValidationWorker(
            ReadingViewValidationOperations validation
    ) {
        this.validation = Objects.requireNonNull(
                validation,
                "validation"
        );
    }

    @Scheduled(
            fixedDelayString =
                    "${app.analytics.validation.poll-interval:1s}"
    )
    public void poll() {
        for (int bucket = 0; bucket < MAXIMUM_BATCH_SIZE; bucket++) {
            if (!validation.processNext(workerId)) {
                return;
            }
        }
    }
}
