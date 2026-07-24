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

public final class ReadingCompletionAnalyticsHandler
        implements IntegrationEventHandler {

    private final RawReadingEventRepository events;
    private final ReadingSessionPseudonymizer pseudonyms;
    private final ObjectMapper mapper;

    public ReadingCompletionAnalyticsHandler(
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
        return "raw-reading-completions";
    }

    @Override
    public String eventType() {
        return ReadingSessionEvents.COMPLETED;
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
                    ReadingSessionEvents.ReadingSessionCompleted.class
            );
            if (!payload.sessionId().equals(event.aggregateId())
                    || !payload.completionId().equals(
                            event.correlationId()
                    )) {
                throw new IllegalArgumentException(
                        "reading completion event envelope mismatch"
                );
            }
            events.append(List.of(new RawReadingEvent(
                    payload.completionId(),
                    RawReadingEvent.Kind.COMPLETION,
                    pseudonyms.pseudonymize(payload.sessionId()),
                    payload.storyId(),
                    payload.chapterId(),
                    payload.finalSequence(),
                    payload.occurredAt(),
                    payload.position(),
                    null,
                    event.occurredAt()
            )));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "reading completion event payload is invalid",
                    exception
            );
        }
    }
}
