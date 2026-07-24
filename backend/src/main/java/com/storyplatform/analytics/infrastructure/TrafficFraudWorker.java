package com.storyplatform.analytics.infrastructure;

import com.storyplatform.analytics.application.TrafficFraudOperations;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;
import java.util.UUID;

public final class TrafficFraudWorker {

    private static final int MAXIMUM_BATCH_SIZE = 50;
    private final TrafficFraudOperations fraud;
    private final String workerId = UUID.randomUUID().toString();

    public TrafficFraudWorker(TrafficFraudOperations fraud) {
        this.fraud = Objects.requireNonNull(fraud, "fraud");
    }

    @Scheduled(
            fixedDelayString =
                    "${app.analytics.fraud.poll-interval:1s}"
    )
    public void poll() {
        for (int item = 0; item < MAXIMUM_BATCH_SIZE; item++) {
            if (!fraud.processNext(workerId)) {
                return;
            }
        }
    }
}
