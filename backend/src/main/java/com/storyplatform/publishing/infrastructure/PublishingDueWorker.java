package com.storyplatform.publishing.infrastructure;

import com.storyplatform.publishing.application.PublishingDueOperations;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;
import java.util.UUID;

public final class PublishingDueWorker {

    private static final int MAXIMUM_BATCH_SIZE = 16;

    private final PublishingDueOperations publishing;
    private final String workerId = UUID.randomUUID().toString();

    public PublishingDueWorker(PublishingDueOperations publishing) {
        this.publishing = Objects.requireNonNull(
                publishing,
                "publishing"
        );
    }

    @Scheduled(
            fixedDelayString =
                    "${app.publishing.due-worker.poll-interval:2s}"
    )
    public void poll() {
        for (int item = 0; item < MAXIMUM_BATCH_SIZE; item++) {
            if (!publishing.processNext(workerId)) {
                return;
            }
        }
    }
}
