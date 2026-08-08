package com.storyplatform.shared.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Versioned metadata and payload for a cross-module integration event.
 *
 * <p>Payloads must be minimal event-specific DTOs and must never contain
 * credentials, access tokens, raw payment data, or unnecessary PII.</p>
 */
public record IntegrationEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        String aggregateType,
        String aggregateId,
        String actorId,
        String teamId,
        Object payload
) {

    private static final Pattern EVENT_TYPE = Pattern.compile(
            "[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*){2,}"
    );
    private static final Pattern AGGREGATE_TYPE = Pattern.compile(
            "[a-z][a-z0-9_-]{0,63}"
    );
    private static final Pattern SAFE_ID = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"
    );

    public IntegrationEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(payload, "payload");
        requireMatch(eventType, EVENT_TYPE, 128, "eventType");
        if (eventVersion < 1 || eventVersion > 999) {
            throw new IllegalArgumentException(
                    "eventVersion must be between 1 and 999"
            );
        }
        requireMatch(correlationId, SAFE_ID, 128, "correlationId");
        requireMatch(
                aggregateType,
                AGGREGATE_TYPE,
                64,
                "aggregateType"
        );
        requireMatch(aggregateId, SAFE_ID, 128, "aggregateId");
        requireOptionalId(actorId, "actorId");
        requireOptionalId(teamId, "teamId");
    }

    private static void requireOptionalId(String value, String field) {
        if (value != null) {
            requireMatch(value, SAFE_ID, 128, field);
        }
    }

    private static void requireMatch(
            String value,
            Pattern pattern,
            int maxLength,
            String field
    ) {
        if (value == null
                || value.length() > maxLength
                || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    field + " has an invalid format"
            );
        }
    }
}
