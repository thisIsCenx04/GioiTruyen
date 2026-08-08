package com.storyplatform.reading.application;

import java.util.List;

public interface ReadingHistoryOperations {

    HistoryPage list(
            String userId,
            String cursor,
            int limit
    );

    void delete(String userId, String storyId);

    record HistoryPage(
            List<ReadingProgressOperations.ProgressView> items,
            String nextCursor,
            boolean hasMore
    ) {
        public HistoryPage {
            items = List.copyOf(items);
        }
    }
}
