package com.storyplatform.notifications.application.port;

import com.storyplatform.notifications.application
        .NotificationPreferenceOperations;

import java.time.Instant;
import java.util.Optional;

public interface NotificationPreferenceRepository {

    Optional<NotificationPreferenceOperations.PreferenceView> find(
            String userId
    );

    SaveOutcome save(
            NotificationPreferenceOperations.PreferenceView preference,
            long expectedVersion
    );

    SaveOutcome disable(
            String userId,
            Channel channel,
            Instant now
    );

    enum SaveOutcome {
        SUCCESS,
        CONFLICT,
        NOT_FOUND
    }

    enum Channel {
        EMAIL,
        PUSH
    }
}
