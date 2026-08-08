package com.storyplatform.analytics.infrastructure;

import com.storyplatform.analytics.application.RawReadingEvent;
import com.storyplatform.analytics.application.port.RawReadingEventRepository;
import com.storyplatform.analytics.application.port.ReadingSessionPseudonymizer;
import com.storyplatform.reading.application.contract.ReadingSessionEvents;
import com.storyplatform.shared.events.OutboxDelivery;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Outbox handler that converts {@link ReadingSessionEvents#COMPLETED} events
 * into a single {@link RawReadingEvent} of kind {@code COMPLETION}.
 */
public final class ReadingCompletionAnalyticsHandler {

    private final RawReadingEventRepository repository;
    private final ReadingSessionPseudonymizer pseudonymizer;
    private final ObjectMapper mapper;

    public ReadingCompletionAnalyticsHandler(
            RawReadingEventRepository repository,
            ReadingSessionPseudonymizer pseudonymizer,
            ObjectMapper mapper
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.pseudonymizer = Objects.requireNonNull(pseudonymizer, "pseudonymizer");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public String consumer() {
        return "raw-reading-completions";
    }

    public int eventVersion() {
        return 1;
    }

    public void handle(OutboxDelivery delivery) {
        ReadingSessionEvents.ReadingSessionCompleted payload;
        try {
            payload = mapper.readValue(
                    delivery.payload(),
                    ReadingSessionEvents.ReadingSessionCompleted.class
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Failed to decode completion payload", exception
            );
        }
        if (!payload.sessionId().equals(delivery.aggregateId())) {
            throw new IllegalArgumentException(
                    "Completion envelope mismatch: sessionId does not match aggregateId"
            );
        }
        String sessionRef = pseudonymizer.pseudonymize(payload.sessionId());
        Instant receivedAt = delivery.occurredAt();
        repository.append(List.of(new RawReadingEvent(
                delivery.eventId(),
                RawReadingEvent.Kind.COMPLETION,
                sessionRef,
                payload.actorRef(),
                payload.storyId(),
                payload.chapterId(),
                payload.finalSequence(),
                payload.occurredAt(),
                payload.position(),
                null,
                receivedAt
        )));
    }
}
