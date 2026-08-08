package com.storyplatform.publishing.application;

import java.time.Instant;

public interface ContentVisibilityOperations {

    VisibilityView teamChange(
            String actorId,
            String teamId,
            String storyId,
            long expectedVersion,
            VisibilityCommand command
    );

    VisibilityView moderationChange(
            String actorId,
            String storyId,
            long expectedVersion,
            VisibilityCommand command
    );

    record VisibilityCommand(
            Action action,
            String reasonCode,
            String note
    ) {
    }

    record VisibilityView(
            String storyId,
            String teamId,
            String state,
            long version,
            Instant changedAt
    ) {
    }

    enum Action {
        HIDE,
        SUSPEND,
        REINSTATE
    }
}
