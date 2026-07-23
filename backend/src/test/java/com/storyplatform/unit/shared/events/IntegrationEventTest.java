package com.storyplatform.unit.shared.events;

import com.storyplatform.shared.events.IntegrationEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class IntegrationEventTest {

    @Test
    void acceptsVersionedNamespacedEventWithSafeMetadata() {
        event("publishing.story.published", 1);
    }

    @Test
    void rejectsUnversionedOrUnnamespacedEvent() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                event("published", 1)
        ).withMessage("eventType has an invalid format");

        assertThatIllegalArgumentException().isThrownBy(() ->
                event("publishing.story.published", 0)
        ).withMessage("eventVersion must be between 1 and 999");
    }

    @Test
    void rejectsUnsafeCorrelationAndAggregateIdentifiers() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new IntegrationEvent(
                        UUID.randomUUID(),
                        "publishing.story.published",
                        1,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        "invalid correlation id",
                        "story",
                        "story-1",
                        null,
                        null,
                        Map.of()
                )
        ).withMessage("correlationId has an invalid format");
    }

    private static IntegrationEvent event(String type, int version) {
        return new IntegrationEvent(
                UUID.randomUUID(),
                type,
                version,
                Instant.parse("2026-01-01T00:00:00Z"),
                "request-1",
                "story",
                "story-1",
                "user-1",
                "team-1",
                Map.of("revision", 3)
        );
    }
}
