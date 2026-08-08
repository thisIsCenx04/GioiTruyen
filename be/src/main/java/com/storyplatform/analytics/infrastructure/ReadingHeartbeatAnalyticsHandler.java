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
 * Outbox handler that converts {@link ReadingSessionEvents#HEARTBEAT_ACCEPTED}
 * events into {@link RawReadingEvent}s and appends them to the repository.
 */
public final class ReadingHeartbeatAnalyticsHandler {

    private final RawReadingEventRepository repository;
    private final ReadingSessionPseudonymizer pseudonymizer;
    private final ObjectMapper mapper;

    public ReadingHeartbeatAnalyticsHandler(
            RawReadingEventRepository repository,
            ReadingSessionPseudonymizer pseudonymizer,
            ObjectMapper mapper
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.pseudonymizer = Objects.requireNonNull(pseudonymizer, "pseudonymizer");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public String consumer() {
        return "raw-reading-heartbeats";
    }

    public int eventVersion() {
        return 1;
    }

    public void handle(OutboxDelivery delivery) {
        ReadingSessionEvents.HeartbeatBatchAccepted payload;
        try {
            payload = mapper.readValue(
                    delivery.payload(),
                    ReadingSessionEvents.HeartbeatBatchAccepted.class
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Failed to decode heartbeat payload", exception
            );
        }
        if (!payload.batchId().equals(delivery.correlationId())) {
            throw new IllegalArgumentException(
                    "Heartbeat envelope mismatch: batchId does not match correlationId"
            );
        }
        String sessionRef = pseudonymizer.pseudonymize(payload.sessionId());
        Instant receivedAt = delivery.occurredAt();
        List<RawReadingEvent> events = payload.heartbeats().stream()
                .map(h -> new RawReadingEvent(
                        delivery.eventId(),
                        RawReadingEvent.Kind.HEARTBEAT,
                        sessionRef,
                        payload.actorRef(),
                        payload.storyId(),
                        payload.chapterId(),
                        h.sequence(),
                        h.occurredAt(),
                        h.position(),
                        h.activeSeconds(),
                        receivedAt
                ))
                .toList();
        repository.append(events);
    }
}
