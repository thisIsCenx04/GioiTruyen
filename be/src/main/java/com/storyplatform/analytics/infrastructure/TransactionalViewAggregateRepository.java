package com.storyplatform.analytics.infrastructure;

import com.storyplatform.analytics.application.ViewAggregateOperations;
import com.storyplatform.analytics.application.port.ViewAggregateRepository;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Transactional decorator over a {@link ViewAggregateRepository} delegate that
 * forwards every operation unchanged. Exists to allow Spring transaction
 * management to be applied at the infrastructure boundary.
 */
public final class TransactionalViewAggregateRepository
        implements ViewAggregateRepository {

    private final ViewAggregateRepository delegate;

    public TransactionalViewAggregateRepository(ViewAggregateRepository delegate) {
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
    public boolean commit(
            Candidate candidate,
            String workerId,
            Delta delta,
            Instant now
    ) {
        return delegate.commit(candidate, workerId, delta, now);
    }

    @Override
    public void retry(Candidate candidate, String workerId, Instant retryAt) {
        delegate.retry(candidate, workerId, retryAt);
    }

    @Override
    public void reset(String storyId, Instant from, Instant to) {
        delegate.reset(storyId, from, to);
    }

    @Override
    public ViewAggregateOperations.Reconciliation reconcile(
            String storyId,
            Instant from,
            Instant to
    ) {
        return delegate.reconcile(storyId, from, to);
    }
}
