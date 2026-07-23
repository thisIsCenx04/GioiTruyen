package com.storyplatform.shared.events.persistence;

import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.observability.TraceContextPropagation;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public class OutboxAppender {

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxProperties properties;
    private final Clock clock;
    private final TraceContextPropagation traceContextPropagation;

    public OutboxAppender(
            MongoTemplate mongoTemplate,
            ObjectMapper objectMapper,
            OutboxProperties properties,
            Clock clock,
            TraceContextPropagation traceContextPropagation
    ) {
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
        this.traceContextPropagation = traceContextPropagation;
    }

    /**
     * Appends an event to the caller's existing MongoDB transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxMessage append(IntegrationEvent event) {
        Objects.requireNonNull(event, "event");
        byte[] serialized = serialize(event.payload());
        if (serialized.length > properties.maxPayloadBytes()) {
            throw new IllegalArgumentException(
                    "Integration event payload exceeds "
                            + properties.maxPayloadBytes()
                            + " bytes"
            );
        }

        Instant createdAt = clock.instant();
        OutboxMessage message = OutboxMessage.pending(
                event,
                new String(serialized, StandardCharsets.UTF_8),
                traceContextPropagation.capture(),
                createdAt
        );
        return mongoTemplate.insert(message);
    }

    private byte[] serialize(Object payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Integration event payload cannot be serialized",
                    exception
            );
        }
    }
}
