package com.storyplatform.unit.shared.events.persistence;

import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import com.storyplatform.shared.events.persistence.OutboxMessage;
import com.storyplatform.shared.events.persistence.OutboxProperties;
import com.storyplatform.shared.events.persistence.OutboxStatus;
import com.storyplatform.shared.observability.TraceContextPropagation;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OutboxAppenderTest {

    private static final Instant NOW = Instant.parse(
            "2026-01-01T00:00:00Z"
    );

    private final JdbcClient jdbc = mock(
            JdbcClient.class,
            RETURNS_DEEP_STUBS
    );
    private final OutboxAppender appender = new OutboxAppender(
            jdbc,
            JsonMapper.builder().build(),
            new OutboxProperties(65_536),
            Clock.fixed(NOW, ZoneOffset.UTC),
            new TraceContextPropagation(OpenTelemetry.noop())
    );

    @Test
    void appendsPendingMessageWithStableEnvelopeAndJsonPayload() {
        IntegrationEvent event = event(Map.of("revision", 3));

        OutboxMessage result = appender.append(event);

        verify(jdbc).sql(org.mockito.ArgumentMatchers.contains(
                "INSERT INTO outbox_messages"
        ));
        assertThat(result.id()).isEqualTo(event.eventId().toString());
        assertThat(result.eventType()).isEqualTo(event.eventType());
        assertThat(result.eventVersion()).isEqualTo(1);
        assertThat(result.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(result.attempts()).isZero();
        assertThat(result.nextAttemptAt()).isEqualTo(NOW);
        assertThat(result.createdAt()).isEqualTo(NOW);
        assertThat(result.traceContext()).isEmpty();
        assertThat(result.payload()).isEqualTo("{\"revision\":3}");
        assertThat(result.leaseOwner()).isNull();
        assertThat(result.processedAt()).isNull();
    }

    @Test
    void oversizedPayloadIsRejectedBeforeDatabaseWrite() {
        IntegrationEvent event = event(Map.of(
                "content",
                "x".repeat(65_536)
        ));

        assertThatIllegalArgumentException().isThrownBy(() ->
                appender.append(event)
        ).withMessage(
                "Integration event payload exceeds 65536 bytes"
        );

        verify(jdbc, never()).sql(org.mockito.ArgumentMatchers.anyString());
    }

    private static IntegrationEvent event(Object payload) {
        return new IntegrationEvent(
                UUID.fromString("581c36b2-52a0-4a18-8035-5478ec1c3270"),
                "publishing.story.published",
                1,
                NOW.minusSeconds(1),
                "request-1",
                "story",
                "story-1",
                "user-1",
                "team-1",
                payload
        );
    }
}
