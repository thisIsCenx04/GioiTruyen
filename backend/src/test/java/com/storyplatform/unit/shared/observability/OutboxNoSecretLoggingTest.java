package com.storyplatform.unit.shared.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.storyplatform.shared.events.persistence.InboxDispatcher;
import com.storyplatform.shared.events.persistence.OutboxMessage;
import com.storyplatform.shared.events.persistence.OutboxMessageStore;
import com.storyplatform.shared.events.persistence.OutboxProcessor;
import com.storyplatform.shared.events.persistence.OutboxStatus;
import com.storyplatform.shared.events.persistence.OutboxWorkerProperties;
import com.storyplatform.shared.events.persistence.RetryBackoff;
import com.storyplatform.shared.observability.OutboxTelemetry;
import com.storyplatform.shared.observability.TraceContextPropagation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxNoSecretLoggingTest {

    private static final Instant NOW = Instant.parse(
            "2026-01-01T00:00:00Z"
    );

    @Test
    void handlerExceptionMessageAndPayloadNeverReachWorkerLogs() {
        OutboxMessageStore store = mock(OutboxMessageStore.class);
        InboxDispatcher dispatcher = mock(InboxDispatcher.class);
        OutboxMessage message = message();
        when(store.claim(
                "worker-1",
                NOW,
                Duration.ofSeconds(30)
        )).thenReturn(Optional.of(message));
        doThrow(new IllegalStateException(
                "SENSITIVE_SENTINEL_47"
        )).when(dispatcher).dispatch(message);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(
                OutboxTelemetry.class
        );
        logger.addAppender(appender);
        try {
            processor(store, dispatcher).processOne("worker-1");
        } finally {
            logger.detachAppender(appender);
        }

        String rendered = appender.list.stream()
                .map(event -> event.getFormattedMessage()
                        + event.getKeyValuePairs())
                .reduce("", (left, right) -> left + right);
        assertThat(rendered)
                .contains("retry")
                .doesNotContain("SENSITIVE_SENTINEL_47")
                .doesNotContain("private chapter payload");
    }

    private static OutboxProcessor processor(
            OutboxMessageStore store,
            InboxDispatcher dispatcher
    ) {
        OutboxWorkerProperties properties = new OutboxWorkerProperties(
                false,
                25,
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                8,
                Duration.ofSeconds(1),
                Duration.ofMinutes(15)
        );
        return new OutboxProcessor(
                store,
                dispatcher,
                properties,
                new RetryBackoff(
                        properties.initialBackoff(),
                        properties.maxBackoff()
                ),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new OutboxTelemetry(
                        ObservationRegistry.create(),
                        new TraceContextPropagation(OpenTelemetry.noop())
                )
        );
    }

    private static OutboxMessage message() {
        return new OutboxMessage(
                "event-1",
                "publishing.story.published",
                1,
                NOW.minusSeconds(1),
                "request-1",
                Map.of(),
                "story",
                "story-1",
                "user-1",
                "team-1",
                "application/json",
                "{\"chapter\":\"private chapter payload\"}",
                OutboxStatus.PROCESSING,
                3,
                NOW,
                "worker-1",
                NOW.plusSeconds(30),
                null,
                NOW.minusSeconds(1),
                null
        );
    }
}
