package com.storyplatform.analytics.application.port;

import com.storyplatform.analytics.application.ViewAggregateOperations;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public interface ViewAggregateRepository {

    Optional<Candidate> claim(
            String workerId,
            Instant now,
            Instant leaseUntil
    );

    boolean commit(
            Candidate candidate,
            String workerId,
            Delta delta,
            Instant now
    );

    void retry(Candidate candidate, String workerId, Instant retryAt);

    void reset(String storyId, Instant from, Instant to);

    ViewAggregateOperations.Reconciliation reconcile(
            String storyId,
            Instant from,
            Instant to
    );

    record Candidate(
            String id,
            String storyId,
            String kind,
            Instant occurredAt,
            boolean valid,
            Set<String> reasons,
            String fraudDecision
    ) {
        public Candidate {
            reasons = Set.copyOf(reasons);
        }
    }

    record Delta(
            long rawEvents,
            long completedViews,
            long validViews,
            long invalidViews,
            Set<String> reasons
    ) {
        public Delta {
            reasons = Set.copyOf(reasons);
        }
    }
}
