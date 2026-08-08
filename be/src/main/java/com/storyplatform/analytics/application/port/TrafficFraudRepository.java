package com.storyplatform.analytics.application.port;

import com.storyplatform.analytics.application.TrafficFraudScorer;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public interface TrafficFraudRepository {

    Optional<Candidate> claim(
            String workerId,
            Instant now,
            Instant leaseUntil
    );

    boolean commit(
            Candidate candidate,
            String workerId,
            TrafficFraudScorer.Score score
    );

    void retry(
            Candidate candidate,
            String workerId,
            Instant retryAt
    );

    record Candidate(
            String id,
            String eventId,
            String actorRef,
            String storyId,
            Set<String> signals
    ) {
        public Candidate {
            signals = Set.copyOf(signals);
        }
    }
}
