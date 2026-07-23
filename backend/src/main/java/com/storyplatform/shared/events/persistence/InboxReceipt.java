package com.storyplatform.shared.events.persistence;

import com.storyplatform.shared.events.IntegrationEvent;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

@Document(collection = InboxReceipt.COLLECTION)
public record InboxReceipt(
        @Id String id,
        String consumer,
        String eventId,
        String eventType,
        int eventVersion,
        Instant processedAt
) {

    public static final String COLLECTION = "inbox_messages";

    private static final Pattern CONSUMER = Pattern.compile(
            "[a-z][a-z0-9-]{0,63}"
    );

    public InboxReceipt {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(processedAt, "processedAt");
        if (consumer == null || !CONSUMER.matcher(consumer).matches()) {
            throw new IllegalArgumentException(
                    "consumer has an invalid format"
            );
        }
        if (eventVersion < 1) {
            throw new IllegalArgumentException(
                    "eventVersion must be positive"
            );
        }
        if (!id.equals(consumer + ":" + eventId)) {
            throw new IllegalArgumentException(
                    "id must match consumer and eventId"
            );
        }
    }

    public static InboxReceipt from(
            String consumer,
            IntegrationEvent event,
            Instant processedAt
    ) {
        Objects.requireNonNull(event, "event");
        return new InboxReceipt(
                consumer + ":" + event.eventId(),
                consumer,
                event.eventId().toString(),
                event.eventType(),
                event.eventVersion(),
                processedAt
        );
    }

    public static InboxReceipt from(
            String consumer,
            OutboxMessage message,
            Instant processedAt
    ) {
        Objects.requireNonNull(message, "message");
        return new InboxReceipt(
                consumer + ":" + message.id(),
                consumer,
                message.id(),
                message.eventType(),
                message.eventVersion(),
                processedAt
        );
    }
}
