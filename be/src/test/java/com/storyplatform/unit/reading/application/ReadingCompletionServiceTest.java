package com.storyplatform.unit.reading.application;

import com.storyplatform.reading.application.ReadingCompletionOperations;
import com.storyplatform.reading.application.ReadingCompletionService;
import com.storyplatform.reading.application.ReadingSessionException;
import com.storyplatform.reading.application.port.ReadingCompletionRepository;
import com.storyplatform.reading.application.port.ReadingSessionTokenCodec;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadingCompletionServiceTest {

    private static final String SESSION =
            "40000000-0000-4000-8000-000000000001";
    private static final String COMPLETION =
            "60000000-0000-4000-8000-000000000001";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final ReadingCompletionRepository repository =
            mock(ReadingCompletionRepository.class);
    private final ReadingSessionTokenCodec tokens =
            mock(ReadingSessionTokenCodec.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);

    @Test
    void completesThroughOutboxWithoutIncrementingViewsDirectly() {
        claims();
        when(repository.complete(
                SESSION, "actor", COMPLETION, 2, NOW, NOW
        )).thenReturn(ReadingCompletionRepository.CompleteResult.APPLIED);

        var receipt = service().complete(
                SESSION, "token", command()
        );

        assertThat(receipt.status()).isEqualTo("COMPLETION_PENDING");
        assertThat(receipt.duplicate()).isFalse();
        ArgumentCaptor<IntegrationEvent> event =
                ArgumentCaptor.forClass(IntegrationEvent.class);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().eventType())
                .isEqualTo("reading.session.completed");
        assertThat(event.getValue().payload())
                .isInstanceOf(
                        com.storyplatform.reading.application.contract
                                .ReadingSessionEvents
                                .ReadingSessionCompleted.class
                );
    }

    @Test
    void safelyAcknowledgesACompletionRetryOnce() {
        claims();
        when(repository.complete(
                SESSION, "actor", COMPLETION, 2, NOW, NOW
        )).thenReturn(ReadingCompletionRepository.CompleteResult.DUPLICATE);

        var receipt = service().complete(
                SESSION, "token", command()
        );

        assertThat(receipt.duplicate()).isTrue();
        verify(outbox, never()).append(any());
    }

    @Test
    void rejectsTimeoutSequenceConflictForgeryAndUnsafeValues() {
        claims();
        when(repository.complete(
                SESSION, "actor", COMPLETION, 2, NOW, NOW
        )).thenReturn(
                ReadingCompletionRepository.CompleteResult.SEQUENCE_CONFLICT,
                ReadingCompletionRepository.CompleteResult.NOT_ACTIVE
        );
        assertThatThrownBy(() -> service().complete(
                SESSION, "token", command()
        )).isInstanceOf(ReadingSessionException.class);
        assertThatThrownBy(() -> service().complete(
                SESSION, "token", command()
        )).isInstanceOf(ReadingSessionException.class);

        when(tokens.verify("forged", NOW))
                .thenThrow(new IllegalArgumentException("forged"));
        assertThatThrownBy(() -> service().complete(
                SESSION, "forged", command()
        )).isInstanceOf(ReadingSessionException.class);

        claims();
        assertThatThrownBy(() -> service().complete(
                SESSION,
                "token",
                new ReadingCompletionOperations.CompletionCommand(
                        COMPLETION,
                        -1,
                        NOW,
                        Double.NaN
                )
        )).isInstanceOf(ReadingSessionException.class);
    }

    private void claims() {
        when(tokens.verify("token", NOW)).thenReturn(
                new ReadingSessionTokenCodec.Claims(
                        SESSION,
                        "20000000-0000-4000-8000-000000000001",
                        "30000000-0000-4000-8000-000000000001",
                        "actor",
                        NOW.minusSeconds(60),
                        NOW.plusSeconds(60)
                )
        );
    }

    private ReadingCompletionService service() {
        return new ReadingCompletionService(
                repository,
                tokens,
                outbox,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static ReadingCompletionOperations.CompletionCommand command() {
        return new ReadingCompletionOperations.CompletionCommand(
                COMPLETION,
                2,
                NOW,
                100
        );
    }
}
