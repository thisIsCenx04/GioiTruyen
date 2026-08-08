package com.storyplatform.reading.application.port;

import com.storyplatform.reading.application.ReadingProgressOperations;

import java.time.Instant;
import java.util.List;

public interface ReadingHistoryRepository {

    List<ReadingProgressOperations.ProgressView> list(
            String userId,
            Instant beforeUpdatedAt,
            String beforeStoryId,
            int limit
    );

    void delete(String userId, String storyId);
}
