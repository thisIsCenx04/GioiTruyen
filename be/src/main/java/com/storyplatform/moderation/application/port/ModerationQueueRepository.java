package com.storyplatform.moderation.application.port;

import com.storyplatform.moderation.application.ModerationQueueOperations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ModerationQueueRepository {

    List<ModerationQueueOperations.ReviewCase> findClaimable(
            ModerationQueueCursorCodec.Cursor after,
            int limit,
            Instant now
    );

    Optional<ModerationQueueOperations.ReviewCase> claim(
            String reviewId,
            String reviewerId,
            long expectedVersion,
            Instant now,
            Instant leaseUntil
    );

    Optional<ModerationQueueOperations.ReviewDetail> find(String reviewId);
}
