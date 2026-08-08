package com.storyplatform.notifications.application.port;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public interface NotificationDeliveryRepository {

    Optional<DeliveryJob> claim(
            String workerId,
            Instant now,
            Instant leaseUntil,
            int maximumAttempts
    );

    Optional<DeliveryTarget> target(DeliveryJob job);

    boolean complete(
            DeliveryJob job,
            String workerId,
            String providerMessageId,
            Instant now
    );

    boolean suppress(
            DeliveryJob job,
            String workerId,
            String reason,
            Instant now
    );

    boolean reschedule(
            DeliveryJob job,
            String workerId,
            String failureCode,
            Instant nextAttemptAt,
            boolean deadLetter,
            Instant now
    );

    record DeliveryJob(
            String id,
            String notificationId,
            String recipientId,
            Channel channel,
            String category,
            String type,
            String title,
            String body,
            Map<String, String> data,
            int attempt
    ) {
        public DeliveryJob {
            data = Map.copyOf(data);
        }
    }

    record DeliveryTarget(
            String address,
            Map<String, String> credentials
    ) {
        public DeliveryTarget {
            credentials = Map.copyOf(credentials);
        }

        public DeliveryTarget(String address) {
            this(address, Map.of());
        }
    }

    enum Channel {
        EMAIL,
        PUSH
    }
}
