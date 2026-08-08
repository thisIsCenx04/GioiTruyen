package com.storyplatform.reading.application.port;

import com.storyplatform.reading.application.ReadingProgressOperations;

import java.time.Instant;
import java.util.Optional;

public interface ReadingProgressRepository {

    Optional<StoredProgress> find(String userId, String storyId);

    boolean chapterIsPublished(String storyId, String chapterId);

    boolean create(
            String userId,
            ReadingProgressOperations.ProgressView progress
    );

    boolean update(
            String userId,
            ReadingProgressOperations.ProgressView progress,
            long expectedVersion,
            Instant previousDeviceUpdatedAt
    );

    record StoredProgress(
            String userId,
            ReadingProgressOperations.ProgressView progress
    ) {
    }
}
