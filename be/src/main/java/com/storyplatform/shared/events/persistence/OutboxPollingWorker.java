package com.storyplatform.shared.events.persistence;

import com.storyplatform.shared.observability.OutboxTelemetry;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.UUID;

public class OutboxPollingWorker {

    private final OutboxProcessor processor;
    private final OutboxTelemetry telemetry;
    private final String owner = UUID.randomUUID().toString();

    public OutboxPollingWorker(
            OutboxProcessor processor,
            OutboxTelemetry telemetry
    ) {
        this.processor = processor;
        this.telemetry = telemetry;
    }

    @Scheduled(
            fixedDelayString =
                    "${app.events.outbox.worker.poll-interval:1s}"
    )
    public void poll() {
        telemetry.observePoll(() -> processor.processBatch(owner));
    }
}
