package com.storyplatform.shared.events.persistence;

import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.observability.TraceContextPropagation;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public class OutboxAppender {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final OutboxProperties properties;
    private final Clock clock;
    private final TraceContextPropagation traceContextPropagation;

    public OutboxAppender(
            JdbcClient jdbc,
            ObjectMapper objectMapper,
            OutboxProperties properties,
            Clock clock,
            TraceContextPropagation traceContextPropagation
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
        this.traceContextPropagation = traceContextPropagation;
    }

    /**
     * Appends an event to the caller's existing relational transaction.
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
        jdbc.sql("""
                        INSERT INTO outbox_messages (
                            id, event_type, event_version, occurred_at,
                            correlation_id, trace_context, aggregate_type,
                            aggregate_id, actor_id, team_id, content_type,
                            payload, status, attempts, next_attempt_at,
                            lease_owner, lease_until, last_error,
                            created_at, processed_at
                        ) VALUES (
                            :id, :eventType, :eventVersion, :occurredAt,
                            :correlationId, :traceContext, :aggregateType,
                            :aggregateId, :actorId, :teamId, :contentType,
                            :payload, :status, :attempts, :nextAttemptAt,
                            :leaseOwner, :leaseUntil, :lastError,
                            :createdAt, :processedAt
                        )
                        """)
                .param("id", message.id())
                .param("eventType", message.eventType())
                .param("eventVersion", message.eventVersion())
                .param("occurredAt", message.occurredAt())
                .param("correlationId", message.correlationId())
                .param(
                        "traceContext",
                        json(message.traceContext())
                )
                .param("aggregateType", message.aggregateType())
                .param("aggregateId", message.aggregateId())
                .param("actorId", message.actorId())
                .param("teamId", message.teamId())
                .param("contentType", message.contentType())
                .param("payload", message.payload())
                .param("status", message.status().name())
                .param("attempts", message.attempts())
                .param("nextAttemptAt", message.nextAttemptAt())
                .param("leaseOwner", message.leaseOwner())
                .param("leaseUntil", message.leaseUntil())
                .param("lastError", message.lastError())
                .param("createdAt", message.createdAt())
                .param("processedAt", message.processedAt())
                .update();
        return message;
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

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Integration event metadata cannot be serialized",
                    exception
            );
        }
    }
}
