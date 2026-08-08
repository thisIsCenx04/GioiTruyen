package com.storyplatform.publishing.application.port;

import com.storyplatform.publishing.domain.PublishingSchedule;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PublishingScheduleRepository {

    Optional<PublishingSchedule> findActive(
            String teamId,
            String storyId
    );

    Optional<ApprovedCandidate> findApproved(
            String teamId,
            String storyId,
            String revision
    );

    boolean create(
            ApprovedCandidate candidate,
            PublishingSchedule schedule
    );

    boolean reschedule(
            PublishingSchedule current,
            Instant publishAt,
            String timeZone,
            Instant updatedAt,
            long expectedVersion
    );

    boolean cancel(
            PublishingSchedule current,
            Instant updatedAt,
            long expectedVersion
    );

    record ApprovedCandidate(
            String storyId,
            String teamId,
            String revision,
            long storyVersion,
            List<PublishingSchedule.FrozenChapterRevision> chapters
    ) {
        public ApprovedCandidate {
            chapters = List.copyOf(chapters);
        }
    }
}
