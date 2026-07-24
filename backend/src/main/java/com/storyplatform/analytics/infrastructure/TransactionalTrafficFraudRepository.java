package com.storyplatform.analytics.infrastructure;

import com.storyplatform.analytics.application.TrafficFraudScorer;
import com.storyplatform.analytics.application.port.TrafficFraudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class TransactionalTrafficFraudRepository
        implements TrafficFraudRepository {

    private final TrafficFraudRepository delegate;

    public TransactionalTrafficFraudRepository(
            TrafficFraudRepository delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public Optional<Candidate> claim(
            String workerId,
            Instant now,
            Instant leaseUntil
    ) {
        return delegate.claim(workerId, now, leaseUntil);
    }

    @Override
    @Transactional
    public boolean commit(
            Candidate candidate,
            String workerId,
            TrafficFraudScorer.Score score
    ) {
        return delegate.commit(candidate, workerId, score);
    }

    @Override
    public void retry(
            Candidate candidate,
            String workerId,
            Instant retryAt
    ) {
        delegate.retry(candidate, workerId, retryAt);
    }
}
