package com.storyplatform.analytics.application;

import com.storyplatform.analytics.application.port.ViewAggregateRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public final class ViewAggregateService implements ViewAggregateOperations {

    private static final Duration MAXIMUM_REBUILD_RANGE =
            Duration.ofDays(31);
    private final ViewAggregateRepository repository;
    private final Clock clock;
    private final Duration lease;
    private final Duration retryDelay;

    public ViewAggregateService(
            ViewAggregateRepository repository,
            Clock clock,
            Duration lease,
            Duration retryDelay
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
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
        Instant now = clock.instant();
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
            if (!repository.commit(
                    candidate,
                    workerId,
                    delta(candidate),
                    clock.instant()
            )) {
                throw new IllegalStateException("aggregate lease lost");
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

    @Override
    public void rebuild(String storyId, Instant from, Instant to) {
        Range range = range(storyId, from, to);
        repository.reset(range.storyId(), range.from(), range.to());
    }

    @Override
    public Reconciliation reconcile(
            String storyId,
            Instant from,
            Instant to
    ) {
        Range range = range(storyId, from, to);
        return repository.reconcile(
                range.storyId(),
                range.from(),
                range.to()
        );
    }

    private static ViewAggregateRepository.Delta delta(
            ViewAggregateRepository.Candidate candidate
    ) {
        boolean completed = "COMPLETION".equals(candidate.kind());
        boolean valid = completed
                && candidate.valid()
                && "PASS".equals(candidate.fraudDecision());
        return new ViewAggregateRepository.Delta(
                1,
                completed ? 1 : 0,
                valid ? 1 : 0,
                completed && !valid ? 1 : 0,
                candidate.reasons()
        );
    }

    private static Range range(
            String storyId,
            Instant from,
            Instant to
    ) {
        String story;
        try {
            story = UUID.fromString(storyId).toString();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("storyId is invalid");
        }
        if (from == null
                || to == null
                || !to.isAfter(from)
                || Duration.between(from, to)
                        .compareTo(MAXIMUM_REBUILD_RANGE) > 0) {
            throw new IllegalArgumentException(
                    "aggregate range must be positive and at most 31 days"
            );
        }
        Instant end = to.truncatedTo(ChronoUnit.DAYS);
        if (!end.equals(to)) {
            end = end.plus(1, ChronoUnit.DAYS);
        }
        return new Range(
                story,
                from.truncatedTo(ChronoUnit.DAYS),
                end
        );
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private record Range(String storyId, Instant from, Instant to) {
    }
}
