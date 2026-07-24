package com.storyplatform.publishing.application;

import com.storyplatform.publishing.application.port
        .PublishingScheduleRepository;
import com.storyplatform.publishing.domain.PublishingSchedule;
import com.storyplatform.teams.application.contract.TeamPermissionAuthorizer;
import com.storyplatform.teams.application.contract.TeamStatusDirectory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class PublishingScheduleService
        implements PublishingScheduleOperations {

    public static final String PUBLISH_PERMISSION = "story:publish";
    static final Duration MINIMUM_DELAY = Duration.ofMinutes(1);
    static final Duration MAXIMUM_DELAY = Duration.ofDays(365);

    private final TeamPermissionAuthorizer permissions;
    private final TeamStatusDirectory teams;
    private final PublishingScheduleRepository schedules;
    private final Supplier<String> identifiers;
    private final Clock clock;

    public PublishingScheduleService(
            TeamPermissionAuthorizer permissions,
            TeamStatusDirectory teams,
            PublishingScheduleRepository schedules,
            Supplier<String> identifiers,
            Clock clock
    ) {
        this.permissions = Objects.requireNonNull(
                permissions,
                "permissions"
        );
        this.teams = Objects.requireNonNull(teams, "teams");
        this.schedules = Objects.requireNonNull(schedules, "schedules");
        this.identifiers = Objects.requireNonNull(
                identifiers,
                "identifiers"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ScheduleView schedule(
            String actorId,
            String teamId,
            String storyId,
            ScheduleCommand command
    ) {
        authorize(actorId, teamId);
        String story = uuid(storyId, "storyId");
        if (command == null) {
            throw invalid("Schedule body is required.");
        }
        String revision = uuid(command.revision(), "revision");
        Instant now = clock.instant();
        ScheduledTime time = time(
                command.publishAt(),
                command.timeZone(),
                now
        );
        var active = schedules.findActive(teamId, story);
        if (active.isPresent()) {
            PublishingSchedule previous = active.orElseThrow();
            if (previous.revision().equals(revision)
                    && previous.publishAt().equals(time.instant())
                    && previous.timeZone().equals(time.zone())) {
                return view(previous);
            }
            throw rejected(
                    "PUBLISHING_SCHEDULE_EXISTS",
                    "The story already has an active schedule.",
                    PublishingScheduleException.Kind.CONFLICT
            );
        }
        PublishingScheduleRepository.ApprovedCandidate candidate =
                schedules.findApproved(teamId, story, revision)
                        .orElseThrow(() -> rejected(
                                "APPROVED_REVISION_NOT_FOUND",
                                "The approved Team revision does not exist.",
                                PublishingScheduleException.Kind.NOT_FOUND
                        ));
        PublishingSchedule schedule = new PublishingSchedule(
                uuid(identifiers.get(), "scheduleId"),
                PublishingSchedule.TargetType.STORY,
                story,
                teamId,
                revision,
                candidate.chapters(),
                PublishingSchedule.State.SCHEDULED,
                time.instant(),
                time.zone(),
                actorId,
                now,
                now,
                1
        );
        if (!schedules.create(candidate, schedule)) {
            throw race();
        }
        return view(schedule);
    }

    @Override
    public ScheduleView reschedule(
            String actorId,
            String teamId,
            String storyId,
            long expectedVersion,
            RescheduleCommand command
    ) {
        authorize(actorId, teamId);
        PublishingSchedule current = active(
                teamId,
                uuid(storyId, "storyId")
        );
        requireVersion(current, expectedVersion);
        if (command == null) {
            throw invalid("Reschedule body is required.");
        }
        Instant now = clock.instant();
        ScheduledTime time = time(
                command.publishAt(),
                command.timeZone(),
                now
        );
        if (!schedules.reschedule(
                current,
                time.instant(),
                time.zone(),
                now,
                expectedVersion
        )) {
            throw stale();
        }
        return view(new PublishingSchedule(
                current.id(),
                current.targetType(),
                current.targetId(),
                current.teamId(),
                current.revision(),
                current.chapterRevisions(),
                current.state(),
                time.instant(),
                time.zone(),
                current.createdBy(),
                current.createdAt(),
                now,
                expectedVersion + 1
        ));
    }

    @Override
    public void cancel(
            String actorId,
            String teamId,
            String storyId,
            long expectedVersion
    ) {
        authorize(actorId, teamId);
        PublishingSchedule current = active(
                teamId,
                uuid(storyId, "storyId")
        );
        requireVersion(current, expectedVersion);
        if (!schedules.cancel(
                current,
                clock.instant(),
                expectedVersion
        )) {
            throw stale();
        }
    }

    private void authorize(String actorId, String teamId) {
        if (!teams.isActive(teamId)
                || !permissions.allows(
                actorId,
                teamId,
                PUBLISH_PERMISSION
        )) {
            throw rejected(
                    "PUBLISHING_SCHEDULE_FORBIDDEN",
                    "An active Team membership with story:publish is required.",
                    PublishingScheduleException.Kind.FORBIDDEN
            );
        }
    }

    private PublishingSchedule active(String teamId, String storyId) {
        return schedules.findActive(teamId, storyId).orElseThrow(
                () -> rejected(
                        "PUBLISHING_SCHEDULE_NOT_FOUND",
                        "The active Team schedule does not exist.",
                        PublishingScheduleException.Kind.NOT_FOUND
                )
        );
    }

    private static void requireVersion(
            PublishingSchedule current,
            long expectedVersion
    ) {
        if (expectedVersion < 1
                || current.version() != expectedVersion) {
            throw stale();
        }
    }

    private static ScheduledTime time(
            OffsetDateTime value,
            String zoneValue,
            Instant now
    ) {
        if (value == null || zoneValue == null) {
            throw invalid("publishAt and timeZone are required.");
        }
        final ZoneId zone;
        try {
            zone = ZoneId.of(zoneValue);
        } catch (RuntimeException exception) {
            throw invalid("timeZone must be a valid IANA zone.");
        }
        Instant publishAt = value.toInstant();
        if (!zone.getRules().getOffset(publishAt).equals(value.getOffset())) {
            throw invalid("publishAt offset does not match timeZone.");
        }
        Duration delay = Duration.between(now, publishAt);
        if (delay.compareTo(MINIMUM_DELAY) < 0
                || delay.compareTo(MAXIMUM_DELAY) > 0) {
            throw invalid(
                    "publishAt must be 1 minute to 365 days in the future."
            );
        }
        return new ScheduledTime(publishAt, zone.getId());
    }

    private static ScheduleView view(PublishingSchedule schedule) {
        return new ScheduleView(
                schedule.id(),
                schedule.targetId(),
                schedule.teamId(),
                schedule.revision(),
                schedule.chapterRevisions().size(),
                schedule.state().name(),
                schedule.publishAt(),
                schedule.timeZone(),
                schedule.version(),
                schedule.createdAt(),
                schedule.updatedAt()
        );
    }

    private static String uuid(String value, String field) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw invalid(field + " must be a UUID.");
        }
    }

    private static PublishingScheduleException race() {
        return rejected(
                "PUBLISHING_SCHEDULE_RACE",
                "The approved revision changed while it was scheduled.",
                PublishingScheduleException.Kind.CONFLICT
        );
    }

    private static PublishingScheduleException stale() {
        return rejected(
                "PUBLISHING_SCHEDULE_STALE",
                "The schedule version is stale.",
                PublishingScheduleException.Kind.PRECONDITION
        );
    }

    private static PublishingScheduleException invalid(String message) {
        return rejected(
                "PUBLISHING_SCHEDULE_INVALID",
                message,
                PublishingScheduleException.Kind.INVALID
        );
    }

    private static PublishingScheduleException rejected(
            String code,
            String message,
            PublishingScheduleException.Kind kind
    ) {
        return new PublishingScheduleException(code, message, kind);
    }

    private record ScheduledTime(Instant instant, String zone) {
    }
}
