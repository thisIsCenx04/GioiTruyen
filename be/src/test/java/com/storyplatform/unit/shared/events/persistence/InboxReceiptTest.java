package com.storyplatform.unit.shared.events.persistence;

import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.InboxReceipt;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class InboxReceiptTest {

    @Test
    void receiptUsesDeterministicConsumerEventIdentity() {
        IntegrationEvent event = event();
        Instant processedAt = Instant.parse("2026-01-01T00:01:00Z");

        InboxReceipt receipt = InboxReceipt.from(
                "search-indexer",
                event,
                processedAt
        );

        assertThat(receipt.id()).isEqualTo(
                "search-indexer:" + event.eventId()
        );
        assertThat(receipt.consumer()).isEqualTo("search-indexer");
        assertThat(receipt.eventId()).isEqualTo(event.eventId().toString());
        assertThat(receipt.processedAt()).isEqualTo(processedAt);
    }

    @Test
    void unsafeConsumerNameIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                InboxReceipt.from(
                        "Search Indexer",
                        event(),
                        Instant.parse("2026-01-01T00:01:00Z")
                )
        ).withMessage("consumer has an invalid format");
    }

    @Test
    void inconsistentDedupeIdentityIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new InboxReceipt(
                        "wrong-id",
                        "search-indexer",
                        event().eventId().toString(),
                        event().eventType(),
                        event().eventVersion(),
                        Instant.parse("2026-01-01T00:01:00Z")
                )
        ).withMessage("id must match consumer and eventId");
    }

    private static IntegrationEvent event() {
        return new IntegrationEvent(
                UUID.fromString("581c36b2-52a0-4a18-8035-5478ec1c3270"),
                "publishing.story.published",
                1,
                Instant.parse("2026-01-01T00:00:00Z"),
                "request-1",
                "story",
                "story-1",
                null,
                null,
                Map.of("revision", 3)
        );
    }
}
