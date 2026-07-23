package com.storyplatform.shared.events.persistence;

import org.springframework.scheduling.annotation.Scheduled;

import java.util.UUID;

public class OutboxPollingWorker {

    private final OutboxProcessor processor;
    private final String owner = UUID.randomUUID().toString();

    public OutboxPollingWorker(OutboxProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(
            fixedDelayString =
                    "${app.events.outbox.worker.poll-interval:1s}"
    )
    public void poll() {
        processor.processBatch(owner);
    }
}
