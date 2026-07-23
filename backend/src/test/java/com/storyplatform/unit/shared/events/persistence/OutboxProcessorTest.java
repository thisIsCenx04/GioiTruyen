package com.storyplatform.unit.shared.events.persistence;

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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxProcessorTest {

    private static final Instant NOW = Instant.parse(
            "2026-01-01T00:00:00Z"
    );
    private static final String OWNER = "worker-1";

    private final OutboxMessageStore store = mock(OutboxMessageStore.class);
    private final InboxDispatcher dispatcher = mock(InboxDispatcher.class);
    private final OutboxWorkerProperties properties =
            new OutboxWorkerProperties(
                    false,
                    25,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(1),
                    8,
                    Duration.ofSeconds(1),
                    Duration.ofMinutes(15)
            );
    private final OutboxProcessor processor = new OutboxProcessor(
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

    @Test
    void noClaimStopsWithoutDispatching() {
        when(store.claim(OWNER, NOW, Duration.ofSeconds(30)))
                .thenReturn(Optional.empty());

        assertThat(processor.processOne(OWNER)).isFalse();

        verify(dispatcher, never()).dispatch(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void successfulDispatchCompletesOwnedMessage() {
        OutboxMessage message = processingMessage(1);
        when(store.claim(OWNER, NOW, Duration.ofSeconds(30)))
                .thenReturn(Optional.of(message));

        assertThat(processor.processOne(OWNER)).isTrue();

        verify(dispatcher).dispatch(message);
        verify(store).complete(message.id(), OWNER, NOW);
    }

    @Test
    void failedDispatchSchedulesBoundedRetryWithoutLeakingError() {
        OutboxMessage message = processingMessage(3);
        when(store.claim(OWNER, NOW, Duration.ofSeconds(30)))
                .thenReturn(Optional.of(message));
        doThrow(new IllegalStateException("secret provider detail"))
                .when(dispatcher)
                .dispatch(message);

        processor.processOne(OWNER);

        verify(store).scheduleRetry(
                message.id(),
                OWNER,
                NOW.plusSeconds(4),
                "HANDLER_FAILED"
        );
        verify(store, never()).deadLetter(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void finalFailedAttemptMovesMessageToDeadLetter() {
        OutboxMessage message = processingMessage(8);
        when(store.claim(OWNER, NOW, Duration.ofSeconds(30)))
                .thenReturn(Optional.of(message));
        doThrow(new IllegalStateException("failed"))
                .when(dispatcher)
                .dispatch(message);

        processor.processOne(OWNER);

        verify(store).deadLetter(
                message.id(),
                OWNER,
                NOW,
                "HANDLER_FAILED"
        );
        verify(store, never()).scheduleRetry(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void batchStopsWhenNoMoreMessagesCanBeClaimed() {
        OutboxMessage message = processingMessage(1);
        when(store.claim(OWNER, NOW, Duration.ofSeconds(30)))
                .thenReturn(Optional.of(message))
                .thenReturn(Optional.empty());

        assertThat(processor.processBatch(OWNER)).isEqualTo(1);

        verify(store).complete(message.id(), OWNER, NOW);
    }

    private static OutboxMessage processingMessage(int attempts) {
        return new OutboxMessage(
                "event-1",
                "publishing.story.published",
                1,
                NOW.minusSeconds(1),
                "request-1",
                java.util.Map.of(),
                "story",
                "story-1",
                "user-1",
                "team-1",
                "application/json",
                "{\"revision\":3}",
                OutboxStatus.PROCESSING,
                attempts,
                NOW,
                OWNER,
                NOW.plusSeconds(30),
                null,
                NOW.minusSeconds(1),
                null
        );
    }
}
