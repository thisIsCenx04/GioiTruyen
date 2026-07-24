package com.storyplatform.notifications.application;

import java.time.Instant;

public interface PushSubscriptionOperations {

    SubscriptionView register(
            String userId,
            SubscriptionCommand command
    );

    void remove(String userId, String subscriptionId);

    record SubscriptionCommand(
            String endpoint,
            String p256dh,
            String auth
    ) {
    }

    record SubscriptionView(
            String id,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
