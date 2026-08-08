package com.storyplatform.analytics.application;

import java.time.Instant;

public interface ViewAggregateOperations {

    boolean processNext(String workerId);

    void rebuild(String storyId, Instant from, Instant to);

    Reconciliation reconcile(String storyId, Instant from, Instant to);

    record Reconciliation(
            long expectedRawEvents,
            long actualRawEvents,
            long expectedCompletedViews,
            long actualCompletedViews,
            long expectedValidViews,
            long actualValidViews,
            long expectedInvalidViews,
            long actualInvalidViews
    ) {
        public boolean matches() {
            return expectedRawEvents == actualRawEvents
                    && expectedCompletedViews == actualCompletedViews
                    && expectedValidViews == actualValidViews
                    && expectedInvalidViews == actualInvalidViews;
        }
    }
}
