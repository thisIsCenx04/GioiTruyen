package com.storyplatform.notifications.application;

import java.time.Instant;
import java.util.Set;

public interface NotificationPreferenceOperations {

    PreferenceView get(String userId);

    PreferenceView update(
            String userId,
            long expectedVersion,
            PreferenceCommand command
    );

    PreferenceView unsubscribe(String token);

    record PreferenceCommand(
            boolean emailEnabled,
            boolean pushEnabled,
            Set<String> categories,
            boolean consentGranted
    ) {
        public PreferenceCommand {
            categories = categories == null
                    ? Set.of()
                    : Set.copyOf(categories);
        }
    }

    record PreferenceView(
            String userId,
            boolean emailEnabled,
            boolean pushEnabled,
            Set<String> categories,
            String consentVersion,
            Instant consentedAt,
            Instant updatedAt,
            long version
    ) {
        public PreferenceView {
            categories = Set.copyOf(categories);
        }
    }
}
