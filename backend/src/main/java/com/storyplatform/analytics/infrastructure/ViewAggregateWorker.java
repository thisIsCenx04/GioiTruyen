package com.storyplatform.analytics.infrastructure;

import com.storyplatform.analytics.application.ViewAggregateOperations;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;
import java.util.UUID;

public final class ViewAggregateWorker {

    private static final int MAXIMUM_BATCH_SIZE = 100;
    private final ViewAggregateOperations aggregates;
    private final String workerId = UUID.randomUUID().toString();

    public ViewAggregateWorker(ViewAggregateOperations aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    @Scheduled(
            fixedDelayString =
                    "${app.analytics.aggregates.poll-interval:1s}"
    )
    public void poll() {
        for (int item = 0; item < MAXIMUM_BATCH_SIZE; item++) {
            if (!aggregates.processNext(workerId)) {
                return;
            }
        }
    }
}
