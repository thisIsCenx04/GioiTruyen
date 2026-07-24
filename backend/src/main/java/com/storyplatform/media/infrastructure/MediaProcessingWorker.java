package com.storyplatform.media.infrastructure;

import com.storyplatform.media.application.MediaProcessingOperations;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;
import java.util.UUID;

public final class MediaProcessingWorker {

    private static final int MAXIMUM_BATCH_SIZE = 8;

    private final MediaProcessingOperations processing;
    private final String workerId = UUID.randomUUID().toString();

    public MediaProcessingWorker(MediaProcessingOperations processing) {
        this.processing = Objects.requireNonNull(processing, "processing");
    }

    @Scheduled(
            fixedDelayString =
                    "${app.media.cloudinary.processing-poll-interval:2s}"
    )
    public void poll() {
        for (int item = 0; item < MAXIMUM_BATCH_SIZE; item++) {
            if (!processing.processNext(workerId)) {
                return;
            }
        }
    }
}
