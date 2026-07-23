package com.storyplatform.shared.events.persistence;

import com.storyplatform.shared.events.IntegrationEvent;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = OutboxMessage.COLLECTION)
public record OutboxMessage(
        @Id String id,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        String aggregateType,
        String aggregateId,
        String actorId,
        String teamId,
        String contentType,
        String payload,
        OutboxStatus status,
        int attempts,
        Instant nextAttemptAt,
        String leaseOwner,
        Instant leaseUntil,
        String lastError,
        Instant createdAt,
        Instant processedAt
) {

    public static final String COLLECTION = "outbox_messages";
    public static final String JSON_CONTENT_TYPE = "application/json";

    static OutboxMessage pending(
            IntegrationEvent event,
            String payload,
            Instant createdAt
    ) {
        return new OutboxMessage(
                event.eventId().toString(),
                event.eventType(),
                event.eventVersion(),
                event.occurredAt(),
                event.correlationId(),
                event.aggregateType(),
                event.aggregateId(),
                event.actorId(),
                event.teamId(),
                JSON_CONTENT_TYPE,
                payload,
                OutboxStatus.PENDING,
                0,
                createdAt,
                null,
                null,
                null,
                createdAt,
                null
        );
    }
}
