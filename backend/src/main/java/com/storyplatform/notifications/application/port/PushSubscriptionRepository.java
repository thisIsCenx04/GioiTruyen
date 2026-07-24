package com.storyplatform.notifications.application.port;

import com.storyplatform.notifications.application.PushSubscriptionOperations;

import java.time.Instant;

public interface PushSubscriptionRepository {

    PushSubscriptionOperations.SubscriptionView save(
            String id,
            String userId,
            String endpoint,
            String p256dh,
            String auth,
            Instant now
    );

    boolean remove(String id, String userId, Instant now);
}
