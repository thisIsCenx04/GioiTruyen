package com.storyplatform.moderation.application;

import com.storyplatform.moderation.application.port
        .ModerationQueueCursorCodec;
import com.storyplatform.moderation.application.port
        .ModerationQueueRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ModerationQueueService
        implements ModerationQueueOperations {

    private static final int MAXIMUM_PAGE_SIZE = 100;

    private final ModerationQueueRepository repository;
    private final ModerationQueueCursorCodec cursors;
    private final Clock clock;
    private final Duration claimLease;

    public ModerationQueueService(
            ModerationQueueRepository repository,
            ModerationQueueCursorCodec cursors,
            Clock clock,
            Duration claimLease
    ) {
        this.repository = Objects.requireNonNull(
                repository,
                "repository"
        );
        this.cursors = Objects.requireNonNull(cursors, "cursors");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (claimLease == null
                || claimLease.isZero()
                || claimLease.isNegative()) {
            throw new IllegalArgumentException(
                    "claimLease must be positive"
            );
        }
        this.claimLease = claimLease;
    }

    @Override
    public ReviewPage list(int limit, String cursor) {
        if (limit < 1 || limit > MAXIMUM_PAGE_SIZE) {
            throw invalid("Queue limit must be between 1 and 100.");
        }
        ModerationQueueCursorCodec.Cursor after = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                after = cursors.decode(cursor);
            } catch (IllegalArgumentException exception) {
                throw invalid("Queue cursor is invalid.");
            }
        }
        List<ReviewCase> fetched = repository.findClaimable(
                after,
                limit + 1,
                clock.instant()
        );
        boolean hasMore = fetched.size() > limit;
        List<ReviewCase> items = hasMore
                ? List.copyOf(fetched.subList(0, limit))
                : List.copyOf(fetched);
        String next = null;
        if (hasMore) {
            ReviewCase last = items.getLast();
            next = cursors.encode(new ModerationQueueCursorCodec.Cursor(
                    last.priority(),
                    last.submittedAt(),
                    last.id()
            ));
        }
        return new ReviewPage(items, next);
    }

    @Override
    public ReviewCase claim(
            String reviewerId,
            String reviewId,
            long expectedVersion
    ) {
        String reviewer = uuid(reviewerId, "reviewerId");
        String id = uuid(reviewId, "reviewId");
        if (expectedVersion < 1) {
            throw invalid("A positive review version is required.");
        }
        Instant now = clock.instant();
        return repository.claim(
                id,
                reviewer,
                expectedVersion,
                now,
                now.plus(claimLease)
        ).orElseThrow(() -> new ModerationQueueException(
                "REVIEW_CLAIM_CONFLICT",
                "The review was claimed, changed, or is no longer open.",
                ModerationQueueException.Kind.CONFLICT
        ));
    }

    private static String uuid(String value, String field) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw invalid(field + " must be a UUID.");
        }
    }

    private static ModerationQueueException invalid(String message) {
        return new ModerationQueueException(
                "MODERATION_QUEUE_INVALID",
                message,
                ModerationQueueException.Kind.INVALID
        );
    }
}
