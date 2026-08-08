package com.storyplatform.unit.analytics.application;

import com.storyplatform.analytics.application.TrafficFraudScorer;
import com.storyplatform.analytics.application.TrafficFraudService;
import com.storyplatform.analytics.application.port.TrafficFraudRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrafficFraudServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");
    private final TrafficFraudRepository repository =
            mock(TrafficFraudRepository.class);

    @Test
    void returnsFalseWhenThereIsNoClassification() {
        when(repository.claim(any(), any(), any()))
                .thenReturn(Optional.empty());
        assertThat(service(repository).processNext("worker_01")).isFalse();
    }

    @Test
    void storesPassWithoutOpeningCaseAndHoldsMultiSignalTraffic() {
        var pass = candidate(Set.of());
        when(repository.claim(any(), any(), any()))
                .thenReturn(Optional.of(pass));
        when(repository.commit(eq(pass), eq("worker_01"), any()))
                .thenReturn(true);
        assertThat(service(repository).processNext("worker_01")).isTrue();

        var suspicious = candidate(Set.of(
                "SELF_VIEW",
                "IMPOSSIBLE_PROGRESS",
                "ACTIVE_TIME_EXCEEDS_CADENCE"
        ));
        var secondRepository = mock(TrafficFraudRepository.class);
        when(secondRepository.claim(any(), any(), any()))
                .thenReturn(Optional.of(suspicious));
        when(secondRepository.commit(
                eq(suspicious), eq("worker_02"), any()
        )).thenReturn(true);
        assertThat(service(secondRepository).processNext("worker_02"))
                .isTrue();
    }

    @Test
    void retriesStorageFailureOrLostLease() {
        var candidate = candidate(Set.of("DUPLICATE", "SELF_VIEW"));
        when(repository.claim(any(), any(), any()))
                .thenReturn(Optional.of(candidate));
        when(repository.commit(eq(candidate), any(), any()))
                .thenReturn(false);

        assertThat(service(repository).processNext("worker_01")).isTrue();
        verify(repository).retry(
                candidate,
                "worker_01",
                NOW.plusSeconds(30)
        );
    }

    @Test
    void validatesWorkerAndDurations() {
        assertThatThrownBy(() -> service(repository).processNext("bad"))
                .isInstanceOf(IllegalArgumentException.class);
        for (Duration duration : new Duration[]{
                null, Duration.ZERO, Duration.ofSeconds(-1)
        }) {
            assertThatThrownBy(() -> new TrafficFraudService(
                    repository,
                    new TrafficFraudScorer(),
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    duration,
                    Duration.ofSeconds(1)
            )).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static TrafficFraudService service(
            TrafficFraudRepository repository
    ) {
        return new TrafficFraudService(
                repository,
                new TrafficFraudScorer(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(1),
                Duration.ofSeconds(30)
        );
    }

    private static TrafficFraudRepository.Candidate candidate(
            Set<String> signals
    ) {
        return new TrafficFraudRepository.Candidate(
                "classification",
                "event",
                "actor",
                "story",
                signals
        );
    }
}
