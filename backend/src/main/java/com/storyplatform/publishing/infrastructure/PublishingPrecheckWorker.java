package com.storyplatform.publishing.infrastructure;

import com.storyplatform.publishing.application
        .PublishingPrecheckOperations;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;
import java.util.UUID;

public final class PublishingPrecheckWorker {

    private static final int MAXIMUM_BATCH_SIZE = 8;

    private final PublishingPrecheckOperations prechecks;
    private final String workerId = UUID.randomUUID().toString();

    public PublishingPrecheckWorker(
            PublishingPrecheckOperations prechecks
    ) {
        this.prechecks = Objects.requireNonNull(
                prechecks,
                "prechecks"
        );
    }

    @Scheduled(
            fixedDelayString =
                    "${app.publishing.prechecks.poll-interval:2s}"
    )
    public void poll() {
        for (int item = 0; item < MAXIMUM_BATCH_SIZE; item++) {
            if (!prechecks.processNext(workerId)) {
                return;
            }
        }
    }
}
