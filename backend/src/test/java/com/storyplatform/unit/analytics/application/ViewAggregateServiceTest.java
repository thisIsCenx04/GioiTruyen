package com.storyplatform.unit.analytics.application;

import com.storyplatform.analytics.application.ViewAggregateOperations;
import com.storyplatform.analytics.application.ViewAggregateService;
import com.storyplatform.analytics.application.port.ViewAggregateRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

class ViewAggregateServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");
    private static final String STORY =
            "20000000-0000-4000-8000-000000000001";

    @Test
    void countsOnlyCompletedValidatedPassAsAValidView() {
        ViewAggregateRepository repository =
                mock(ViewAggregateRepository.class);
        var candidate = candidate("COMPLETION", true, "PASS");
        when(repository.claim(any(), any(), any()))
                .thenReturn(Optional.of(candidate));
        when(repository.commit(eq(candidate), eq("worker_01"), any(), eq(NOW)))
                .thenReturn(true);

        assertThat(service(repository).processNext("worker_01")).isTrue();

        ArgumentCaptor<ViewAggregateRepository.Delta> delta =
                ArgumentCaptor.forClass(
                        ViewAggregateRepository.Delta.class
                );
        verify(repository).commit(
                eq(candidate), eq("worker_01"), delta.capture(), eq(NOW)
        );
        assertThat(delta.getValue().rawEvents()).isEqualTo(1);
        assertThat(delta.getValue().completedViews()).isEqualTo(1);
        assertThat(delta.getValue().validViews()).isEqualTo(1);
        assertThat(delta.getValue().invalidViews()).isZero();
    }

    @Test
    void separatesHeartbeatAndHeldOrInvalidCompletionCounts() {
        assertDelta(candidate("HEARTBEAT", true, "PASS"), 0, 0);
        assertDelta(candidate("COMPLETION", true, "HOLD_FOR_REVIEW"), 0, 1);
        assertDelta(candidate("COMPLETION", false, "PASS"), 0, 1);
    }

    @Test
    void retriesLostLeaseAndReturnsFalseForEmptyQueue() {
        ViewAggregateRepository repository =
                mock(ViewAggregateRepository.class);
        when(repository.claim(any(), any(), any()))
                .thenReturn(Optional.empty());
        assertThat(service(repository).processNext("worker_01")).isFalse();

        var candidate = candidate("COMPLETION", true, "PASS");
        when(repository.claim(any(), any(), any()))
                .thenReturn(Optional.of(candidate));
        when(repository.commit(any(), any(), any(), any()))
                .thenReturn(false);
        assertThat(service(repository).processNext("worker_01")).isTrue();
        verify(repository).retry(
                candidate, "worker_01", NOW.plusSeconds(30)
        );
    }

    @Test
    void rebuildsWholeUtcDaysAndDelegatesReconciliation() {
        ViewAggregateRepository repository =
                mock(ViewAggregateRepository.class);
        Instant from = Instant.parse("2026-07-24T12:00:00Z");
        Instant to = Instant.parse("2026-07-25T00:00:00Z");
        var expected = new ViewAggregateOperations.Reconciliation(
                3, 3, 2, 2, 1, 1, 1, 1
        );
        when(repository.reconcile(
                STORY,
                Instant.parse("2026-07-24T00:00:00Z"),
                to
        )).thenReturn(expected);
        var service = service(repository);

        service.rebuild(STORY, from, to);
        assertThat(service.reconcile(STORY, from, to)).isEqualTo(expected);
        verify(repository).reset(
                STORY,
                Instant.parse("2026-07-24T00:00:00Z"),
                to
        );
        assertThat(expected.matches()).isTrue();
    }

    @Test
    void validatesWorkerConfigurationStoryAndMaintenanceRange() {
        ViewAggregateRepository repository =
                mock(ViewAggregateRepository.class);
        var service = service(repository);
        assertThatThrownBy(() -> service.processNext("bad"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.rebuild(
                "bad", NOW, NOW.plusSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.rebuild(
                STORY, NOW, NOW
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.rebuild(
                STORY, NOW, NOW.plus(Duration.ofDays(32))
        )).isInstanceOf(IllegalArgumentException.class);
        for (Duration duration : new Duration[]{
                null, Duration.ZERO, Duration.ofSeconds(-1)
        }) {
            assertThatThrownBy(() -> new ViewAggregateService(
                    repository,
                    Clock.systemUTC(),
                    duration,
                    Duration.ofSeconds(1)
            )).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static void assertDelta(
            ViewAggregateRepository.Candidate candidate,
            long valid,
            long invalid
    ) {
        ViewAggregateRepository repository =
                mock(ViewAggregateRepository.class);
        when(repository.claim(any(), any(), any()))
                .thenReturn(Optional.of(candidate));
        when(repository.commit(any(), any(), any(), any()))
                .thenReturn(true);
        service(repository).processNext("worker_01");
        ArgumentCaptor<ViewAggregateRepository.Delta> delta =
                ArgumentCaptor.forClass(
                        ViewAggregateRepository.Delta.class
                );
        verify(repository).commit(
                eq(candidate), eq("worker_01"), delta.capture(), eq(NOW)
        );
        assertThat(delta.getValue().validViews()).isEqualTo(valid);
        assertThat(delta.getValue().invalidViews()).isEqualTo(invalid);
    }

    private static ViewAggregateService service(
            ViewAggregateRepository repository
    ) {
        return new ViewAggregateService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(1),
                Duration.ofSeconds(30)
        );
    }

    private static ViewAggregateRepository.Candidate candidate(
            String kind,
            boolean valid,
            String fraudDecision
    ) {
        return new ViewAggregateRepository.Candidate(
                "classification",
                STORY,
                kind,
                NOW,
                valid,
                Set.of("DUPLICATE"),
                fraudDecision
        );
    }
}
