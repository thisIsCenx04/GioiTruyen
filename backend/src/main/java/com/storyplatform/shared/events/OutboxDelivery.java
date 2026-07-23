package com.storyplatform.shared.events;

import com.storyplatform.shared.events.persistence.OutboxMessage;

import java.time.Instant;

public record OutboxDelivery(
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        String aggregateType,
        String aggregateId,
        String actorId,
        String teamId,
        String contentType,
        String payload
) {

    public static OutboxDelivery from(OutboxMessage message) {
        return new OutboxDelivery(
                message.id(),
                message.eventType(),
                message.eventVersion(),
                message.occurredAt(),
                message.correlationId(),
                message.aggregateType(),
                message.aggregateId(),
                message.actorId(),
                message.teamId(),
                message.contentType(),
                message.payload()
        );
    }
}
