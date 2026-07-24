package com.storyplatform.publishing.infrastructure.persistence;

import com.storyplatform.publishing.domain.PublishingSchedule;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = MongoPublishingScheduleDocument.COLLECTION)
public record MongoPublishingScheduleDocument(
        @Id String id,
        PublishingSchedule.TargetType targetType,
        String targetId,
        String teamId,
        String revision,
        List<PublishingSchedule.FrozenChapterRevision> chapterRevisions,
        PublishingSchedule.State state,
        Instant publishAt,
        String timeZone,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        Instant cancelledAt,
        long version
) {
    public static final String COLLECTION = "publishing_schedules";

    public static MongoPublishingScheduleDocument from(
            PublishingSchedule schedule
    ) {
        return new MongoPublishingScheduleDocument(
                schedule.id(),
                schedule.targetType(),
                schedule.targetId(),
                schedule.teamId(),
                schedule.revision(),
                schedule.chapterRevisions(),
                schedule.state(),
                schedule.publishAt(),
                schedule.timeZone(),
                schedule.createdBy(),
                schedule.createdAt(),
                schedule.updatedAt(),
                null,
                schedule.version()
        );
    }

    public PublishingSchedule toDomain() {
        return new PublishingSchedule(
                id,
                targetType,
                targetId,
                teamId,
                revision,
                chapterRevisions,
                state,
                publishAt,
                timeZone,
                createdBy,
                createdAt,
                updatedAt,
                version
        );
    }
}
