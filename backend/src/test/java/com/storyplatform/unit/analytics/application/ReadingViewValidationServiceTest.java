package com.storyplatform.unit.analytics.application;

import com.storyplatform.analytics.application.RawReadingEvent;
import com.storyplatform.analytics.application.ReadingViewClassifier;
import com.storyplatform.analytics.application
        .ReadingViewValidationService;
import com.storyplatform.analytics.application.port
        .ReadingViewValidationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadingViewValidationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");
    private final ReadingViewValidationRepository repository =
            mock(ReadingViewValidationRepository.class);

    @Test
    void returnsFalseWhenNoBucketIsReady() {
        when(repository.claim(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThat(service().processNext("worker_01")).isFalse();
    }

    @Test
    void classifiesDuplicateSelfAndBotSignalsWithVersionedReasons() {
        var bucket = bucket(List.of(
                event("first", 1, NOW, 0, 0),
                event("second", 2, NOW.plusSeconds(2), 50, 10)
        ));
        when(repository.claim(any(), any(), any(), any()))
                .thenReturn(Optional.of(bucket));
        when(repository.claimFingerprint(any(), eq("first"), any()))
                .thenReturn(false);
        when(repository.claimFingerprint(any(), eq("second"), any()))
                .thenReturn(true);
        when(repository.isSelfView(any()))
                .thenReturn(false, true);
        when(repository.botSignals(any(), any()))
                .thenReturn(Set.of(), Set.of("IMPOSSIBLE_PROGRESS"));
        when(repository.complete(bucket, "worker_01", NOW))
                .thenReturn(true);

        assertThat(service().processNext("worker_01")).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReadingViewClassifier.Classification>> saved =
                ArgumentCaptor.forClass(List.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getFirst().valid()).isTrue();
        assertThat(saved.getValue().get(1).reasons())
                .containsExactlyInAnyOrder(
                        "DUPLICATE",
                        "SELF_VIEW",
                        "IMPOSSIBLE_PROGRESS"
                );
    }

    @Test
    void retriesAClaimAfterFailureOrLostLease() {
        var bucket = bucket(List.of(event("first", 1, NOW, 0, 1)));
        when(repository.claim(any(), any(), any(), any()))
                .thenReturn(Optional.of(bucket));
        when(repository.claimFingerprint(any(), any(), any()))
                .thenReturn(false);
        when(repository.botSignals(any(), any())).thenReturn(Set.of());
        doThrow(new IllegalStateException("storage unavailable"))
                .when(repository).save(anyList());

        assertThat(service().processNext("worker_01")).isTrue();
        verify(repository).retry(
                bucket,
                "worker_01",
                NOW.plusSeconds(30),
                "VALIDATION_FAILED"
        );

        var secondRepository = mock(
                ReadingViewValidationRepository.class
        );
        when(secondRepository.claim(any(), any(), any(), any()))
                .thenReturn(Optional.of(bucket));
        when(secondRepository.botSignals(any(), any()))
                .thenReturn(Set.of());
        when(secondRepository.complete(any(), any(), any()))
                .thenReturn(false);
        var second = service(secondRepository);
        assertThat(second.processNext("worker_02")).isTrue();
        verify(secondRepository).retry(
                bucket,
                "worker_02",
                NOW.plusSeconds(30),
                "VALIDATION_FAILED"
        );
    }

    @Test
    void validatesWorkerAndDurations() {
        assertThatThrownBy(() -> service().processNext("bad"))
                .isInstanceOf(IllegalArgumentException.class);
        for (Duration duration : new Duration[]{
                null, Duration.ZERO, Duration.ofSeconds(-1)
        }) {
            assertThatThrownBy(() -> new ReadingViewValidationService(
                    repository,
                    new ReadingViewClassifier(),
                    Clock.systemUTC(),
                    duration,
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(1)
            )).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private ReadingViewValidationService service() {
        return service(repository);
    }

    private static ReadingViewValidationService service(
            ReadingViewValidationRepository value
    ) {
        return new ReadingViewValidationService(
                value,
                new ReadingViewClassifier(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(2),
                Duration.ofMinutes(1),
                Duration.ofSeconds(30)
        );
    }

    private static ReadingViewValidationRepository.ClaimedBucket bucket(
            List<RawReadingEvent> events
    ) {
        return new ReadingViewValidationRepository.ClaimedBucket(
                "bucket",
                0,
                events.size(),
                events
        );
    }

    private static RawReadingEvent event(
            String id,
            long sequence,
            Instant occurredAt,
            double position,
            int active
    ) {
        return new RawReadingEvent(
                id,
                RawReadingEvent.Kind.HEARTBEAT,
                "a".repeat(64),
                "actor-" + id,
                "20000000-0000-4000-8000-000000000001",
                "30000000-0000-4000-8000-000000000001",
                sequence,
                occurredAt,
                position,
                active,
                NOW
        );
    }
}
