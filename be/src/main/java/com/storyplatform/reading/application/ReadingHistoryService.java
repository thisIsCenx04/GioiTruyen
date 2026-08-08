package com.storyplatform.reading.application;

import com.storyplatform.reading.application.port
        .ReadingHistoryCursorCodec;
import com.storyplatform.reading.application.port.ReadingHistoryRepository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ReadingHistoryService
        implements ReadingHistoryOperations {

    private final ReadingHistoryRepository repository;
    private final ReadingHistoryCursorCodec cursors;

    public ReadingHistoryService(
            ReadingHistoryRepository repository,
            ReadingHistoryCursorCodec cursors
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.cursors = Objects.requireNonNull(cursors);
    }

    @Override
    public HistoryPage list(
            String userId,
            String cursorValue,
            int limit
    ) {
        String user = uuid(userId);
        if (limit < 1 || limit > 100) {
            throw invalid("History limit must be between 1 and 100.");
        }
        Instant beforeUpdatedAt = null;
        String beforeStoryId = null;
        if (cursorValue != null && !cursorValue.isBlank()) {
            ReadingHistoryCursorCodec.Cursor cursor;
            try {
                cursor = cursors.decode(cursorValue);
            } catch (RuntimeException exception) {
                throw invalid("History cursor is invalid.");
            }
            if (!user.equals(cursor.userId())) {
                throw invalid("History cursor belongs to another user.");
            }
            beforeUpdatedAt = cursor.updatedAt();
            beforeStoryId = cursor.storyId();
        }
        List<ReadingProgressOperations.ProgressView> loaded =
                repository.list(
                        user,
                        beforeUpdatedAt,
                        beforeStoryId,
                        limit + 1
                );
        boolean hasMore = loaded.size() > limit;
        List<ReadingProgressOperations.ProgressView> items = hasMore
                ? List.copyOf(loaded.subList(0, limit))
                : List.copyOf(loaded);
        String next = null;
        if (hasMore) {
            var last = items.getLast();
            next = cursors.encode(new ReadingHistoryCursorCodec.Cursor(
                    user,
                    last.updatedAt(),
                    last.storyId()
            ));
        }
        return new HistoryPage(items, next, hasMore);
    }

    @Override
    public void delete(String userId, String storyId) {
        repository.delete(uuid(userId), uuid(storyId));
    }

    private static String uuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw invalid("A history identifier is invalid.");
        }
    }

    private static ReadingProgressException invalid(String detail) {
        return new ReadingProgressException(
                "READING_HISTORY_INVALID",
                detail,
                ReadingProgressException.Kind.INVALID
        );
    }
}
