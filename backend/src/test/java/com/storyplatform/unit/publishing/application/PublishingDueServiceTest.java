package com.storyplatform.unit.publishing.application;

import com.storyplatform.publishing.application.PublishingDueException;
import com.storyplatform.publishing.application.PublishingDueService;
import com.storyplatform.publishing.application.port.PublishingDueRepository;
import com.storyplatform.publishing.domain.PublishingSchedule;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublishingDueServiceTest {

    private static final String WORKER =
            "10000000-0000-4000-8000-000000000001";
    private static final String TEAM =
            "20000000-0000-4000-8000-000000000001";
    private static final String STORY =
            "30000000-0000-4000-8000-000000000001";
    private static final String REVISION =
            "40000000-0000-4000-8000-000000000001";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final PublishingDueRepository repository =
            mock(PublishingDueRepository.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);

    @BeforeEach
    void setUp() {
        when(repository.claim(any(), any(), any()))
                .thenReturn(Optional.of(schedule()));
        when(repository.publish(any(), any(), any())).thenReturn(true);
    }

    @Test
    void publishesOneClaimAndEmitsMinimalEventPerPinnedChapter() {
        ArgumentCaptor<IntegrationEvent> events =
                ArgumentCaptor.forClass(IntegrationEvent.class);

        assertThat(service(true).processNext(WORKER)).isTrue();

        verify(repository).claim(
                WORKER,
                NOW,
                NOW.plusSeconds(30)
        );
        verify(repository).publish(schedule(), WORKER, NOW);
        verify(outbox, org.mockito.Mockito.times(2))
                .append(events.capture());
        assertThat(events.getAllValues())
                .extracting(IntegrationEvent::eventType)
                .containsOnly(PublishingDueService.EVENT_TYPE);
        assertThat(events.getAllValues())
                .extracting(IntegrationEvent::aggregateId)
                .containsExactly(
                        "50000000-0000-4000-8000-000000000001",
                        "50000000-0000-4000-8000-000000000002"
                );
    }

    @Test
    void noDueWorkIsAReplaySafeNoop() {
        when(repository.claim(any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThat(service(true).processNext(WORKER)).isFalse();

        verify(repository, never()).publish(any(), any(), any());
        verify(outbox, never()).append(any());
    }

    @Test
    void inactiveTeamIsDeferredWithoutPublishing() {
        when(repository.defer(any(), any(), any(), any()))
                .thenReturn(true);

        assertThat(service(false).processNext(WORKER)).isTrue();

        verify(repository).defer(
                schedule(),
                WORKER,
                NOW.plusSeconds(300),
                NOW
        );
        verify(repository, never()).publish(any(), any(), any());
        verify(outbox, never()).append(any());
    }

    @Test
    void stalePublishOrDeferralRollsBackWithoutOutbox() {
        when(repository.publish(any(), any(), any())).thenReturn(false);
        assertThatThrownBy(() -> service(true).processNext(WORKER))
                .isInstanceOf(PublishingDueException.class)
                .extracting("code")
                .isEqualTo("PUBLISHING_DUE_STALE");

        when(repository.defer(any(), any(), any(), any()))
                .thenReturn(false);
        assertThatThrownBy(() -> service(false).processNext(WORKER))
                .isInstanceOf(PublishingDueException.class);
        verify(outbox, never()).append(any());
    }

    @Test
    void validatesWorkerAndTimingPolicy() {
        assertThatThrownBy(() -> service(true).processNext("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublishingDueService(
                repository,
                ignored -> true,
                outbox,
                () -> WORKER,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ZERO,
                Duration.ofMinutes(5)
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublishingDueService(
                repository,
                ignored -> true,
                outbox,
                () -> WORKER,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(30),
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private PublishingDueService service(boolean active) {
        ArrayDeque<String> ids = new ArrayDeque<>(List.of(
                "90000000-0000-4000-8000-000000000001",
                "90000000-0000-4000-8000-000000000002"
        ));
        return new PublishingDueService(
                repository,
                ignored -> active,
                outbox,
                ids::removeFirst,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(30),
                Duration.ofMinutes(5)
        );
    }

    private static PublishingSchedule schedule() {
        return new PublishingSchedule(
                "70000000-0000-4000-8000-000000000001",
                PublishingSchedule.TargetType.STORY,
                STORY,
                TEAM,
                REVISION,
                List.of(chapter(1), chapter(2)),
                PublishingSchedule.State.SCHEDULED,
                NOW.minusSeconds(1),
                "UTC",
                "80000000-0000-4000-8000-000000000001",
                NOW.minusSeconds(3600),
                NOW,
                2
        );
    }

    private static PublishingSchedule.FrozenChapterRevision chapter(
            int number
    ) {
        return new PublishingSchedule.FrozenChapterRevision(
                "50000000-0000-4000-8000-00000000000" + number,
                "60000000-0000-4000-8000-00000000000" + number,
                number
        );
    }
}
