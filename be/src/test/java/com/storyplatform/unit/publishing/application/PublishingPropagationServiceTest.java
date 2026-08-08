package com.storyplatform.unit.publishing.application;

import com.storyplatform.publishing.application.EdgePropagationGateway;
import com.storyplatform.publishing.application
        .PublishingPropagationService;
import com.storyplatform.publishing.application.port
        .PublishingPropagationRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublishingPropagationServiceTest {

    private static final String WORKER =
            "10000000-0000-4000-8000-000000000001";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final PublishingPropagationRepository repository =
            mock(PublishingPropagationRepository.class);
    private final EdgePropagationGateway edge =
            mock(EdgePropagationGateway.class);

    @Test
    void deliversClaimOnceAndCompletesExactLease() {
        var task = task();
        when(repository.claimEdge(any(), any(), any()))
                .thenReturn(Optional.of(task));
        when(repository.complete(task, WORKER, NOW)).thenReturn(true);

        assertThat(service().processNext(WORKER)).isTrue();

        verify(repository).claimEdge(
                WORKER, NOW, NOW.plusSeconds(30)
        );
        verify(edge).invalidate(task.eventId(), task.targets());
        verify(repository).complete(task, WORKER, NOW);
    }

    @Test
    void emptyQueueIsNoopAndLostLeaseFails() {
        assertThat(service().processNext(WORKER)).isFalse();
        verify(edge, never()).invalidate(any(), any());

        when(repository.claimEdge(any(), any(), any()))
                .thenReturn(Optional.of(task()));
        assertThatThrownBy(() -> service().processNext(WORKER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validatesWorkerAndLeaseDuration() {
        assertThatThrownBy(() -> service().processNext("bad"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublishingPropagationService(
                repository,
                edge,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ZERO,
                Duration.ofSeconds(5),
                8
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublishingPropagationService(
                repository,
                edge,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(30),
                Duration.ZERO,
                0
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void providerFailureIsBackedOffWithoutLosingPublishedState() {
        when(repository.claimEdge(any(), any(), any()))
                .thenReturn(Optional.of(task()));
        when(repository.fail(
                any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyInt()
        )).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("down"))
                .when(edge).invalidate(any(), any());

        assertThat(service().processNext(WORKER)).isTrue();

        verify(repository).fail(
                task(),
                WORKER,
                NOW,
                NOW.plusSeconds(5),
                "IllegalStateException",
                8
        );
    }

    private PublishingPropagationService service() {
        return new PublishingPropagationService(
                repository,
                edge,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                8
        );
    }

    private static PublishingPropagationRepository.EdgeTask task() {
        return new PublishingPropagationRepository.EdgeTask(
                "event-1:EDGE",
                "event-1",
                List.of(
                        "cloudflare:tag:chapter:one",
                        "next:isr:chapter:one"
                ),
                1
        );
    }
}
