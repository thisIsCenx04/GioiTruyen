package com.storyplatform.integration.persistence;

import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class OutboxTransactionBoundaryIntegrationTest {

    @Autowired
    private OutboxAppender outboxAppender;

    @Test
    void appendOutsideAnExistingTransactionIsRejectedBeforeMongoWrite() {
        IntegrationEvent event = new IntegrationEvent(
                UUID.fromString("581c36b2-52a0-4a18-8035-5478ec1c3270"),
                "publishing.story.published",
                1,
                Instant.parse("2026-01-01T00:00:00Z"),
                "request-1",
                "story",
                "story-1",
                "user-1",
                "team-1",
                Map.of("revision", 3)
        );

        assertThatThrownBy(() -> outboxAppender.append(event))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
