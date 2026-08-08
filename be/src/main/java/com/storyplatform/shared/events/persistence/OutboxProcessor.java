package com.storyplatform.shared.events.persistence;

import com.storyplatform.shared.observability.OutboxTelemetry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class OutboxProcessor {

    static final String HANDLER_FAILURE = "HANDLER_FAILED";

    private final OutboxMessageStore store;
    private final InboxDispatcher dispatcher;
    private final OutboxWorkerProperties properties;
    private final RetryBackoff retryBackoff;
    private final Clock clock;
    private final OutboxTelemetry telemetry;

    public OutboxProcessor(
            OutboxMessageStore store,
            InboxDispatcher dispatcher,
            OutboxWorkerProperties properties,
            RetryBackoff retryBackoff,
            Clock clock,
            OutboxTelemetry telemetry
    ) {
        this.store = store;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.retryBackoff = retryBackoff;
        this.clock = clock;
        this.telemetry = telemetry;
    }

    public int processBatch(String owner) {
        int processed = 0;
        while (processed < properties.batchSize() && processOne(owner)) {
            processed++;
        }
        return processed;
    }

    public boolean processOne(String owner) {
        Instant claimedAt = clock.instant();
        Optional<OutboxMessage> claimed = store.claim(
                owner,
                claimedAt,
                properties.leaseDuration()
        );
        if (claimed.isEmpty()) {
            return false;
        }

        OutboxMessage message = claimed.get();
        try (OutboxTelemetry.Attempt attempt = telemetry.startAttempt(
                message.eventType(),
                message.eventVersion(),
                message.traceContext()
        )) {
            try {
                dispatcher.dispatch(message);
                store.complete(message.id(), owner, clock.instant());
                attempt.outcome(OutboxTelemetry.Outcome.PROCESSED);
            } catch (Exception exception) {
                attempt.outcome(handleFailure(message, owner));
            }
        }
        return true;
    }

    private OutboxTelemetry.Outcome handleFailure(
            OutboxMessage message,
            String owner
    ) {
        Instant failedAt = clock.instant();
        if (message.attempts() >= properties.maxAttempts()) {
            store.deadLetter(
                    message.id(),
                    owner,
                    failedAt,
                    HANDLER_FAILURE
            );
            return OutboxTelemetry.Outcome.DEAD_LETTERED;
        }

        Duration delay = retryBackoff.delayForAttempt(message.attempts());
        store.scheduleRetry(
                message.id(),
                owner,
                failedAt.plus(delay),
                HANDLER_FAILURE
        );
        return OutboxTelemetry.Outcome.RETRY_SCHEDULED;
    }
}
