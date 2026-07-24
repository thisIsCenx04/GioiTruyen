package com.storyplatform.unit.publishing.application;

import com.storyplatform.publishing.application.PublishingScheduleException;
import com.storyplatform.publishing.application.PublishingScheduleOperations;
import com.storyplatform.publishing.application.PublishingScheduleService;
import com.storyplatform.publishing.application.port
        .PublishingScheduleRepository;
import com.storyplatform.publishing.domain.PublishingSchedule;
import com.storyplatform.teams.application.contract.TeamPermissionAuthorizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
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

class PublishingScheduleServiceTest {

    private static final String ACTOR =
            "10000000-0000-4000-8000-000000000001";
    private static final String TEAM =
            "20000000-0000-4000-8000-000000000001";
    private static final String STORY =
            "30000000-0000-4000-8000-000000000001";
    private static final String REVISION =
            "40000000-0000-4000-8000-000000000001";
    private static final String CHAPTER =
            "50000000-0000-4000-8000-000000000001";
    private static final String CHAPTER_REVISION =
            "60000000-0000-4000-8000-000000000001";
    private static final String SCHEDULE =
            "70000000-0000-4000-8000-000000000001";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private static final OffsetDateTime PUBLISH_AT =
            OffsetDateTime.parse("2026-07-25T07:00:00+07:00");

    private final TeamPermissionAuthorizer permissions =
            mock(TeamPermissionAuthorizer.class);
    private final PublishingScheduleRepository repository =
            mock(PublishingScheduleRepository.class);

    @BeforeEach
    void setUp() {
        when(permissions.allows(
                ACTOR,
                TEAM,
                PublishingScheduleService.PUBLISH_PERMISSION
        )).thenReturn(true);
        when(repository.findApproved(TEAM, STORY, REVISION))
                .thenReturn(Optional.of(candidate()));
        when(repository.create(any(), any())).thenReturn(true);
    }

    @Test
    void schedulesApprovedFrozenRevisionInUtc() {
        ArgumentCaptor<PublishingSchedule> schedule =
                ArgumentCaptor.forClass(PublishingSchedule.class);

        var result = service().schedule(
                ACTOR, TEAM, STORY, command()
        );

        verify(repository).create(any(), schedule.capture());
        assertThat(schedule.getValue().publishAt())
                .isEqualTo(Instant.parse("2026-07-25T00:00:00Z"));
        assertThat(schedule.getValue().chapterRevisions())
                .containsExactly(chapter());
        assertThat(result.scheduleId()).isEqualTo(SCHEDULE);
        assertThat(result.timeZone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(result.chapterCount()).isOne();
    }

    @Test
    void safelyReplaysTheExactActiveSchedule() {
        when(repository.findActive(TEAM, STORY))
                .thenReturn(Optional.of(schedule()));

        var result = service().schedule(
                ACTOR, TEAM, STORY, command()
        );

        assertThat(result.scheduleId()).isEqualTo(SCHEDULE);
        verify(repository, never()).findApproved(any(), any(), any());
        verify(repository, never()).create(any(), any());
    }

    @Test
    void rejectsDifferentActiveScheduleAndCreateRace() {
        PublishingSchedule current = schedule();
        when(repository.findActive(TEAM, STORY)).thenReturn(Optional.of(
                new PublishingSchedule(
                        current.id(),
                        current.targetType(),
                        current.targetId(),
                        current.teamId(),
                        current.revision(),
                        current.chapterRevisions(),
                        current.state(),
                        current.publishAt().plusSeconds(60),
                        current.timeZone(),
                        current.createdBy(),
                        current.createdAt(),
                        current.updatedAt(),
                        current.version()
                )
        ));
        assertCode(
                () -> service().schedule(ACTOR, TEAM, STORY, command()),
                "PUBLISHING_SCHEDULE_EXISTS"
        );

        when(repository.findActive(TEAM, STORY))
                .thenReturn(Optional.empty());
        when(repository.create(any(), any())).thenReturn(false);
        assertCode(
                () -> service().schedule(ACTOR, TEAM, STORY, command()),
                "PUBLISHING_SCHEDULE_RACE"
        );
    }

    @Test
    void requiresActiveTeamPermissionAndApprovedRevision() {
        PublishingScheduleService inactive = new PublishingScheduleService(
                permissions,
                ignored -> false,
                repository,
                () -> SCHEDULE,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        assertCode(
                () -> inactive.schedule(ACTOR, TEAM, STORY, command()),
                "PUBLISHING_SCHEDULE_FORBIDDEN"
        );
        when(repository.findApproved(TEAM, STORY, REVISION))
                .thenReturn(Optional.empty());
        assertCode(
                () -> service().schedule(ACTOR, TEAM, STORY, command()),
                "APPROVED_REVISION_NOT_FOUND"
        );
    }

    @Test
    void validatesTimeWindowZoneOffsetAndBody() {
        assertInvalid(null);
        assertInvalid(new PublishingScheduleOperations.ScheduleCommand(
                null, "UTC", REVISION
        ));
        assertInvalid(new PublishingScheduleOperations.ScheduleCommand(
                PUBLISH_AT, "Invalid/Zone", REVISION
        ));
        assertInvalid(new PublishingScheduleOperations.ScheduleCommand(
                PUBLISH_AT, "UTC", REVISION
        ));
        assertInvalid(new PublishingScheduleOperations.ScheduleCommand(
                OffsetDateTime.ofInstant(NOW.plusSeconds(59), ZoneOffset.UTC),
                "UTC",
                REVISION
        ));
        assertInvalid(new PublishingScheduleOperations.ScheduleCommand(
                OffsetDateTime.ofInstant(
                        NOW.plusSeconds(366L * 24 * 60 * 60),
                        ZoneOffset.UTC
                ),
                "UTC",
                REVISION
        ));
    }

    @Test
    void reschedulesAndCancelsWithCompareAndSet() {
        when(repository.findActive(TEAM, STORY))
                .thenReturn(Optional.of(schedule()));
        when(repository.reschedule(
                any(), any(), any(), any(), any(Long.class)
        )).thenReturn(true);
        OffsetDateTime changed =
                OffsetDateTime.parse("2026-07-26T07:00:00+07:00");

        var result = service().reschedule(
                ACTOR,
                TEAM,
                STORY,
                1,
                new PublishingScheduleOperations.RescheduleCommand(
                        changed,
                        "Asia/Ho_Chi_Minh"
                )
        );

        assertThat(result.version()).isEqualTo(2);
        assertThat(result.publishAt())
                .isEqualTo(Instant.parse("2026-07-26T00:00:00Z"));
        when(repository.cancel(any(), any(), any(Long.class)))
                .thenReturn(true);
        service().cancel(ACTOR, TEAM, STORY, 1);
        verify(repository).cancel(any(), any(), any(Long.class));
    }

    @Test
    void rejectsMissingStaleOrRacedScheduleChanges() {
        assertCode(
                () -> service().cancel(ACTOR, TEAM, STORY, 1),
                "PUBLISHING_SCHEDULE_NOT_FOUND"
        );
        when(repository.findActive(TEAM, STORY))
                .thenReturn(Optional.of(schedule()));
        assertCode(
                () -> service().cancel(ACTOR, TEAM, STORY, 2),
                "PUBLISHING_SCHEDULE_STALE"
        );
        when(repository.cancel(any(), any(), any(Long.class)))
                .thenReturn(false);
        assertCode(
                () -> service().cancel(ACTOR, TEAM, STORY, 1),
                "PUBLISHING_SCHEDULE_STALE"
        );
        when(repository.reschedule(
                any(), any(), any(), any(), any(Long.class)
        )).thenReturn(false);
        assertCode(
                () -> service().reschedule(
                        ACTOR,
                        TEAM,
                        STORY,
                        1,
                        new PublishingScheduleOperations.RescheduleCommand(
                                PUBLISH_AT,
                                "Asia/Ho_Chi_Minh"
                        )
                ),
                "PUBLISHING_SCHEDULE_STALE"
        );
        assertCode(
                () -> service().reschedule(
                        ACTOR, TEAM, STORY, 1, null
                ),
                "PUBLISHING_SCHEDULE_INVALID"
        );
    }

    private void assertInvalid(
            PublishingScheduleOperations.ScheduleCommand command
    ) {
        assertCode(
                () -> service().schedule(ACTOR, TEAM, STORY, command),
                "PUBLISHING_SCHEDULE_INVALID"
        );
    }

    private PublishingScheduleService service() {
        return new PublishingScheduleService(
                permissions,
                TEAM::equals,
                repository,
                () -> SCHEDULE,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static PublishingScheduleOperations.ScheduleCommand command() {
        return new PublishingScheduleOperations.ScheduleCommand(
                PUBLISH_AT,
                "Asia/Ho_Chi_Minh",
                REVISION
        );
    }

    private static PublishingScheduleRepository.ApprovedCandidate candidate() {
        return new PublishingScheduleRepository.ApprovedCandidate(
                STORY,
                TEAM,
                REVISION,
                3,
                List.of(chapter())
        );
    }

    private static PublishingSchedule schedule() {
        return new PublishingSchedule(
                SCHEDULE,
                PublishingSchedule.TargetType.STORY,
                STORY,
                TEAM,
                REVISION,
                List.of(chapter()),
                PublishingSchedule.State.SCHEDULED,
                PUBLISH_AT.toInstant(),
                "Asia/Ho_Chi_Minh",
                ACTOR,
                NOW,
                NOW,
                1
        );
    }

    private static PublishingSchedule.FrozenChapterRevision chapter() {
        return new PublishingSchedule.FrozenChapterRevision(
                CHAPTER,
                CHAPTER_REVISION,
                1
        );
    }

    private static void assertCode(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            String code
    ) {
        assertThatThrownBy(action)
                .isInstanceOf(PublishingScheduleException.class)
                .extracting("code")
                .isEqualTo(code);
    }
}
