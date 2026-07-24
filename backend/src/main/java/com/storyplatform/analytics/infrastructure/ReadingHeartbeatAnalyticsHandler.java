package com.storyplatform.analytics.infrastructure;

import com.storyplatform.analytics.application.RawReadingEvent;
import com.storyplatform.analytics.application.port
        .RawReadingEventRepository;
import com.storyplatform.analytics.application.port
        .ReadingSessionPseudonymizer;
import com.storyplatform.reading.application.contract.ReadingSessionEvents;
import com.storyplatform.shared.events.IntegrationEventHandler;
import com.storyplatform.shared.events.OutboxDelivery;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;

public final class ReadingHeartbeatAnalyticsHandler
        implements IntegrationEventHandler {

    private final RawReadingEventRepository events;
    private final ReadingSessionPseudonymizer pseudonyms;
    private final ObjectMapper mapper;

    public ReadingHeartbeatAnalyticsHandler(
            RawReadingEventRepository events,
            ReadingSessionPseudonymizer pseudonyms,
            ObjectMapper mapper
    ) {
        this.events = Objects.requireNonNull(events, "events");
        this.pseudonyms = Objects.requireNonNull(
                pseudonyms,
                "pseudonyms"
        );
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public String consumer() {
        return "raw-reading-heartbeats";
    }

    @Override
    public String eventType() {
        return ReadingSessionEvents.HEARTBEAT_ACCEPTED;
    }

    @Override
    public int eventVersion() {
        return 1;
    }

    @Override
    public void handle(OutboxDelivery event) {
        try {
            var payload = mapper.readValue(
                    event.payload(),
                    ReadingSessionEvents.HeartbeatBatchAccepted.class
            );
            validateEnvelope(
                    event,
                    payload.sessionId(),
                    payload.batchId()
            );
            String sessionRef = pseudonyms.pseudonymize(
                    payload.sessionId()
            );
            List<RawReadingEvent> raw = payload.heartbeats().stream()
                    .map(heartbeat -> new RawReadingEvent(
                            payload.batchId() + ":" + heartbeat.sequence(),
                            RawReadingEvent.Kind.HEARTBEAT,
                            sessionRef,
                            payload.storyId(),
                            payload.chapterId(),
                            heartbeat.sequence(),
                            heartbeat.occurredAt(),
                            heartbeat.position(),
                            heartbeat.activeSeconds(),
                            event.occurredAt()
                    ))
                    .toList();
            events.append(raw);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "reading heartbeat event payload is invalid",
                    exception
            );
        }
    }

    private static void validateEnvelope(
            OutboxDelivery event,
            String sessionId,
            String batchId
    ) {
        if (!sessionId.equals(event.aggregateId())
                || !batchId.equals(event.correlationId())) {
            throw new IllegalArgumentException(
                    "reading heartbeat event envelope mismatch"
            );
        }
    }
}
