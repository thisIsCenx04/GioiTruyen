package com.storyplatform.publishing.application;

import com.storyplatform.publishing.application.port
        .PublishingPropagationRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class PublishingPropagationService
        implements PublishingPropagationOperations {

    private final PublishingPropagationRepository repository;
    private final EdgePropagationGateway edge;
    private final Clock clock;
    private final Duration leaseDuration;
    private final Duration initialBackoff;
    private final int maximumAttempts;

    public PublishingPropagationService(
            PublishingPropagationRepository repository,
            EdgePropagationGateway edge,
            Clock clock,
            Duration leaseDuration,
            Duration initialBackoff,
            int maximumAttempts
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.edge = Objects.requireNonNull(edge);
        this.clock = Objects.requireNonNull(clock);
        if (leaseDuration == null
                || leaseDuration.isZero()
                || leaseDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "leaseDuration must be positive"
            );
        }
        this.leaseDuration = leaseDuration;
        if (initialBackoff == null
                || initialBackoff.isZero()
                || initialBackoff.isNegative()
                || maximumAttempts < 1
                || maximumAttempts > 100) {
            throw new IllegalArgumentException(
                    "propagation retry policy is invalid"
            );
        }
        this.initialBackoff = initialBackoff;
        this.maximumAttempts = maximumAttempts;
    }

    @Override
    public boolean processNext(String workerId) {
        String worker;
        try {
            worker = UUID.fromString(workerId).toString();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "workerId must be a UUID",
                    exception
            );
        }
        Instant now = clock.instant();
        var claimed = repository.claimEdge(
                worker,
                now,
                now.plus(leaseDuration)
        );
        if (claimed.isEmpty()) {
            return false;
        }
        var task = claimed.orElseThrow();
        try {
            edge.invalidate(task.eventId(), task.targets());
        } catch (RuntimeException exception) {
            Instant failedAt = clock.instant();
            if (!repository.fail(
                    task,
                    worker,
                    failedAt,
                    failedAt.plus(backoff(task.attempts())),
                    exception.getClass().getSimpleName(),
                    maximumAttempts
            )) {
                throw new IllegalStateException(
                        "edge propagation failure lease was lost",
                        exception
                );
            }
            return true;
        }
        if (!repository.complete(
                task,
                worker,
                clock.instant()
        )) {
            throw new IllegalStateException(
                    "edge propagation lease was lost"
            );
        }
        return true;
    }

    private Duration backoff(int attempt) {
        int exponent = Math.min(Math.max(attempt - 1, 0), 10);
        return initialBackoff.multipliedBy(1L << exponent);
    }
}
