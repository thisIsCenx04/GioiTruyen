package com.storyplatform.reading.application;

import java.time.Instant;

public interface ReadingProgressOperations {

    ProgressView get(String userId, String storyId);

    ProgressView synchronize(
            String userId,
            String storyId,
            Long expectedVersion,
            SyncCommand command
    );

    record SyncCommand(
            String chapterId,
            double position,
            Instant deviceUpdatedAt
    ) {
    }

    record ProgressView(
            String storyId,
            String chapterId,
            double position,
            Instant deviceUpdatedAt,
            Instant updatedAt,
            long version
    ) {
    }
}
