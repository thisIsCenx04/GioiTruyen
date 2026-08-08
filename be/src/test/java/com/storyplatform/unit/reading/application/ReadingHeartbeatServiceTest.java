package com.storyplatform.unit.reading.application;

import com.storyplatform.reading.application.ReadingHeartbeatOperations;
import com.storyplatform.reading.application.ReadingHeartbeatService;
import com.storyplatform.reading.application.ReadingSessionException;
import com.storyplatform.reading.application.port.ReadingHeartbeatRepository;
import com.storyplatform.reading.application.port.ReadingSessionTokenCodec;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadingHeartbeatServiceTest {

    private static final String SESSION =
            "40000000-0000-4000-8000-000000000001";
    private static final String BATCH =
            "50000000-0000-4000-8000-000000000001";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final ReadingHeartbeatRepository repository =
            mock(ReadingHeartbeatRepository.class);
    private final ReadingSessionTokenCodec tokens =
            mock(ReadingSessionTokenCodec.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);

    @Test
    void atomicallyAdvancesAContiguousBatchAndAppendsAsyncEvent() {
        claims();
        when(repository.apply(
                SESSION, "actor", BATCH, 0, 2, NOW
        )).thenReturn(ReadingHeartbeatRepository.ApplyResult.APPLIED);

        var receipt = service().ingest(
                SESSION,
                "token",
                batch(
                        heartbeat(1, NOW.minusSeconds(15)),
                        heartbeat(2, NOW)
                )
        );

        assertThat(receipt.nextSequence()).isEqualTo(3);
        assertThat(receipt.duplicate()).isFalse();
        ArgumentCaptor<IntegrationEvent> event =
                ArgumentCaptor.forClass(IntegrationEvent.class);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().eventType())
                .isEqualTo("reading.session.heartbeat");
        assertThat(event.getValue().eventId().toString())
                .isEqualTo(BATCH);
    }

    @Test
    void acknowledgesReplayWithoutAppendingAnotherEvent() {
        claims();
        when(repository.apply(
                SESSION, "actor", BATCH, 0, 1, NOW
        )).thenReturn(ReadingHeartbeatRepository.ApplyResult.DUPLICATE);

        var receipt = service().ingest(
                SESSION,
                "token",
                batch(heartbeat(1, NOW))
        );

        assertThat(receipt.duplicate()).isTrue();
        verify(outbox, never()).append(any());
    }

    @Test
    void rejectsSequenceGapsCadenceForgeryAndInactiveSessions() {
        claims();
        assertThatThrownBy(() -> service().ingest(
                SESSION,
                "token",
                batch(
                        heartbeat(1, NOW.minusSeconds(90)),
                        heartbeat(3, NOW)
                )
        )).isInstanceOf(ReadingSessionException.class);

        when(tokens.verify("forged", NOW))
                .thenThrow(new IllegalArgumentException("forged"));
        assertThatThrownBy(() -> service().ingest(
                SESSION,
                "forged",
                batch(heartbeat(1, NOW))
        )).isInstanceOf(ReadingSessionException.class);

        claims();
        when(repository.apply(
                SESSION, "actor", BATCH, 0, 1, NOW
        )).thenReturn(ReadingHeartbeatRepository.ApplyResult.NOT_ACTIVE);
        assertThatThrownBy(() -> service().ingest(
                SESSION,
                "token",
                batch(heartbeat(1, NOW))
        )).isInstanceOf(ReadingSessionException.class)
                .extracting(error -> ((ReadingSessionException) error)
                        .kind())
                .isEqualTo(ReadingSessionException.Kind.NOT_FOUND);
    }

    @Test
    void rejectsRepositorySequenceConflictAndMismatchedTokenPath() {
        claims();
        when(repository.apply(
                SESSION, "actor", BATCH, 0, 1, NOW
        )).thenReturn(
                ReadingHeartbeatRepository.ApplyResult.SEQUENCE_CONFLICT
        );
        assertThatThrownBy(() -> service().ingest(
                SESSION,
                "token",
                batch(heartbeat(1, NOW))
        )).isInstanceOf(ReadingSessionException.class);

        when(tokens.verify("token", NOW)).thenReturn(
                new ReadingSessionTokenCodec.Claims(
                        "40000000-0000-4000-8000-000000000002",
                        "20000000-0000-4000-8000-000000000001",
                        "30000000-0000-4000-8000-000000000001",
                        "actor",
                        NOW.minusSeconds(60),
                        NOW.plusSeconds(60)
                )
        );
        assertThatThrownBy(() -> service().ingest(
                SESSION,
                "token",
                batch(heartbeat(1, NOW))
        )).isInstanceOf(ReadingSessionException.class);
    }

    @Test
    void rejectsUnboundedHeartbeatPayloadsAndIdentifiers() {
        claims();
        assertThatThrownBy(() -> service().ingest(
                "invalid",
                "token",
                batch(heartbeat(1, NOW))
        )).isInstanceOf(ReadingSessionException.class);
        assertThatThrownBy(() -> service().ingest(
                SESSION,
                "token",
                new ReadingHeartbeatOperations.HeartbeatBatch(
                        BATCH,
                        List.of()
                )
        )).isInstanceOf(ReadingSessionException.class);
        var tooMany = java.util.stream.LongStream.rangeClosed(1, 21)
                .mapToObj(sequence -> heartbeat(sequence, NOW.plusSeconds(
                        sequence
                )))
                .toList();
        assertThatThrownBy(() -> service().ingest(
                SESSION,
                "token",
                new ReadingHeartbeatOperations.HeartbeatBatch(
                        BATCH,
                        tooMany
                )
        )).isInstanceOf(ReadingSessionException.class);

        List<ReadingHeartbeatOperations.Heartbeat> invalid = List.of(
                new ReadingHeartbeatOperations.Heartbeat(
                        0, NOW, 50, 15
                ),
                new ReadingHeartbeatOperations.Heartbeat(
                        Long.MAX_VALUE, NOW, 50, 15
                ),
                new ReadingHeartbeatOperations.Heartbeat(
                        1, NOW, -1, 15
                ),
                new ReadingHeartbeatOperations.Heartbeat(
                        1, NOW, 101, 15
                ),
                new ReadingHeartbeatOperations.Heartbeat(
                        1, NOW, Double.NaN, 15
                ),
                new ReadingHeartbeatOperations.Heartbeat(
                        1, NOW, 50, -1
                ),
                new ReadingHeartbeatOperations.Heartbeat(
                        1, NOW, 50, 61
                ),
                heartbeat(1, NOW.minusSeconds(301)),
                heartbeat(1, NOW.plusSeconds(11))
        );
        for (var heartbeat : invalid) {
            assertThatThrownBy(() -> service().ingest(
                    SESSION,
                    "token",
                    batch(heartbeat)
            )).isInstanceOf(ReadingSessionException.class);
        }
    }

    @Test
    void rejectsZeroReverseAndOverlongCadence() {
        claims();
        List<List<ReadingHeartbeatOperations.Heartbeat>> invalid = List.of(
                List.of(heartbeat(1, NOW), heartbeat(2, NOW)),
                List.of(
                        heartbeat(1, NOW),
                        heartbeat(2, NOW.minusSeconds(1))
                ),
                List.of(
                        heartbeat(1, NOW.minusSeconds(61)),
                        heartbeat(2, NOW)
                )
        );
        for (var heartbeats : invalid) {
            assertThatThrownBy(() -> service().ingest(
                    SESSION,
                    "token",
                    new ReadingHeartbeatOperations.HeartbeatBatch(
                            BATCH,
                            heartbeats
                    )
            )).isInstanceOf(ReadingSessionException.class);
        }
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

    private ReadingHeartbeatService service() {
        return new ReadingHeartbeatService(
                repository,
                tokens,
                outbox,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static ReadingHeartbeatOperations.HeartbeatBatch batch(
            ReadingHeartbeatOperations.Heartbeat... heartbeats
    ) {
        return new ReadingHeartbeatOperations.HeartbeatBatch(
                BATCH,
                List.of(heartbeats)
        );
    }

    private static ReadingHeartbeatOperations.Heartbeat heartbeat(
            long sequence,
            Instant occurredAt
    ) {
        return new ReadingHeartbeatOperations.Heartbeat(
                sequence,
                occurredAt,
                50,
                15
        );
    }
}
