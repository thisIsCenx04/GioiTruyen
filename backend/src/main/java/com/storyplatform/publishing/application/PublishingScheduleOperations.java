package com.storyplatform.publishing.application;

import java.time.Instant;
import java.time.OffsetDateTime;

public interface PublishingScheduleOperations {

    ScheduleView schedule(
            String actorId,
            String teamId,
            String storyId,
            ScheduleCommand command
    );

    ScheduleView reschedule(
            String actorId,
            String teamId,
            String storyId,
            long expectedVersion,
            RescheduleCommand command
    );

    void cancel(
            String actorId,
            String teamId,
            String storyId,
            long expectedVersion
    );

    record ScheduleCommand(
            OffsetDateTime publishAt,
            String timeZone,
            String revision
    ) {
    }

    record RescheduleCommand(
            OffsetDateTime publishAt,
            String timeZone
    ) {
    }

    record ScheduleView(
            String scheduleId,
            String storyId,
            String teamId,
            String revision,
            int chapterCount,
            String state,
            Instant publishAt,
            String timeZone,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
