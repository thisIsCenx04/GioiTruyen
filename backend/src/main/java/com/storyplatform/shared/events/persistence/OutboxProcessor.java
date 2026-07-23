package com.storyplatform.shared.events.persistence;

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

    public OutboxProcessor(
            OutboxMessageStore store,
            InboxDispatcher dispatcher,
            OutboxWorkerProperties properties,
            RetryBackoff retryBackoff,
            Clock clock
    ) {
        this.store = store;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.retryBackoff = retryBackoff;
        this.clock = clock;
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
        try {
            dispatcher.dispatch(message);
            store.complete(message.id(), owner, clock.instant());
        } catch (Exception exception) {
            handleFailure(message, owner);
        }
        return true;
    }

    private void handleFailure(OutboxMessage message, String owner) {
        Instant failedAt = clock.instant();
        if (message.attempts() >= properties.maxAttempts()) {
            store.deadLetter(
                    message.id(),
                    owner,
                    failedAt,
                    HANDLER_FAILURE
            );
            return;
        }

        Duration delay = retryBackoff.delayForAttempt(message.attempts());
        store.scheduleRetry(
                message.id(),
                owner,
                failedAt.plus(delay),
                HANDLER_FAILURE
        );
    }
}
