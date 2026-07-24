package com.storyplatform.notifications.infrastructure;

import com.storyplatform.notifications.application
        .NotificationDeliveryOperations;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;
import java.util.UUID;

public final class NotificationDeliveryWorker {

    private static final int MAXIMUM_BATCH_SIZE = 20;
    private final NotificationDeliveryOperations delivery;
    private final String workerId = UUID.randomUUID().toString();

    public NotificationDeliveryWorker(
            NotificationDeliveryOperations delivery
    ) {
        this.delivery = Objects.requireNonNull(delivery, "delivery");
    }

    @Scheduled(
            fixedDelayString =
                    "${app.notifications.delivery.poll-interval:2s}"
    )
    public void poll() {
        for (int item = 0; item < MAXIMUM_BATCH_SIZE; item++) {
            if (!delivery.processNext(workerId)) {
                return;
            }
        }
    }
}
