package com.storyplatform.analytics.application;

import com.storyplatform.analytics.application.port.TrafficFraudRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

public final class TrafficFraudService implements TrafficFraudOperations {

    private final TrafficFraudRepository repository;
    private final TrafficFraudScorer scorer;
    private final Clock clock;
    private final Duration lease;
    private final Duration retryDelay;

    public TrafficFraudService(
            TrafficFraudRepository repository,
            TrafficFraudScorer scorer,
            Clock clock,
            Duration lease,
            Duration retryDelay
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scorer = Objects.requireNonNull(scorer, "scorer");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lease = positive(lease, "lease");
        this.retryDelay = positive(retryDelay, "retryDelay");
    }

    @Override
    public boolean processNext(String workerId) {
        if (workerId == null
                || !workerId.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new IllegalArgumentException("workerId is invalid");
        }
        var now = clock.instant();
        var claimed = repository.claim(
                workerId,
                now,
                now.plus(lease)
        );
        if (claimed.isEmpty()) {
            return false;
        }
        var candidate = claimed.orElseThrow();
        try {
            var score = scorer.score(candidate.signals(), now);
            if (!repository.commit(candidate, workerId, score)) {
                throw new IllegalStateException("fraud scoring lease lost");
            }
        } catch (RuntimeException exception) {
            repository.retry(
                    candidate,
                    workerId,
                    clock.instant().plus(retryDelay)
            );
        }
        return true;
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
