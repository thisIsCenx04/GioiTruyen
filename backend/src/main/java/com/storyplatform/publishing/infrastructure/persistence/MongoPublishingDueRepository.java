package com.storyplatform.publishing.infrastructure.persistence;

import com.storyplatform.publishing.application.port.PublishingDueRepository;
import com.storyplatform.publishing.domain.PublishingSchedule;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class MongoPublishingDueRepository
        implements PublishingDueRepository {

    private static final String STORIES = "stories";
    private static final String CHAPTERS = "chapters";
    private final MongoTemplate mongo;

    public MongoPublishingDueRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public Optional<PublishingSchedule> claim(
            String workerId,
            Instant now,
            Instant leaseUntil
    ) {
        Criteria available = new Criteria().orOperator(
                Criteria.where("leaseUntil").exists(false),
                Criteria.where("leaseUntil").is(null),
                Criteria.where("leaseUntil").lte(now)
        );
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("state").is("SCHEDULED"),
                Criteria.where("publishAt").lte(now),
                available
        )).with(Sort.by(
                Sort.Order.asc("publishAt"),
                Sort.Order.asc("_id")
        ));
        MongoPublishingScheduleDocument claimed = mongo.findAndModify(
                query,
                new Update()
                        .set("leaseOwner", workerId)
                        .set("leaseUntil", leaseUntil)
                        .set("updatedAt", now)
                        .inc("version", 1),
                FindAndModifyOptions.options().returnNew(true),
                MongoPublishingScheduleDocument.class
        );
        return Optional.ofNullable(claimed)
                .map(MongoPublishingScheduleDocument::toDomain);
    }

    @Override
    public boolean defer(
            PublishingSchedule schedule,
            String workerId,
            Instant retryAt,
            Instant updatedAt
    ) {
        var result = mongo.updateFirst(
                claimed(schedule, workerId),
                new Update()
                        .unset("leaseOwner")
                        .set("leaseUntil", retryAt)
                        .set("updatedAt", updatedAt)
                        .inc("version", 1),
                MongoPublishingScheduleDocument.class
        );
        return result.getModifiedCount() == 1;
    }

    @Override
    public boolean publish(
            PublishingSchedule schedule,
            String workerId,
            Instant publishedAt
    ) {
        var scheduleUpdate = mongo.updateFirst(
                claimed(schedule, workerId),
                new Update()
                        .set("state", "PUBLISHED")
                        .set("publishedAt", publishedAt)
                        .set("updatedAt", publishedAt)
                        .unset("leaseOwner")
                        .unset("leaseUntil")
                        .inc("version", 1),
                MongoPublishingScheduleDocument.class
        );
        if (scheduleUpdate.getModifiedCount() != 1) {
            return false;
        }
        var story = mongo.updateFirst(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(schedule.targetId()),
                        Criteria.where("teamId").is(schedule.teamId()),
                        Criteria.where("workflowStatus").is("SCHEDULED"),
                        Criteria.where("currentRevision").is(
                                schedule.revision()
                        )
                )),
                published(schedule.revision(), publishedAt),
                STORIES
        );
        if (story.getModifiedCount() != 1) {
            return false;
        }
        Criteria[] frozen = schedule.chapterRevisions().stream()
                .map(value -> new Criteria().andOperator(
                        Criteria.where("_id").is(value.chapterId()),
                        Criteria.where("currentRevision").is(
                                value.revisionId()
                        )
                ))
                .toArray(Criteria[]::new);
        var chapters = mongo.updateMulti(
                Query.query(new Criteria().andOperator(
                        Criteria.where("storyId").is(
                                schedule.targetId()
                        ),
                        Criteria.where("teamId").is(schedule.teamId()),
                        Criteria.where("workflowStatus").is("SCHEDULED"),
                        new Criteria().orOperator(frozen)
                )),
                new Update()
                        .set("workflowStatus", "PUBLISHED")
                        .set("publishedAt", publishedAt)
                        .set("updatedAt", publishedAt)
                        .unset("scheduledAt")
                        .inc("version", 1),
                CHAPTERS
        );
        return chapters.getModifiedCount()
                == schedule.chapterRevisions().size();
    }

    private static Query claimed(
            PublishingSchedule schedule,
            String workerId
    ) {
        return Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(schedule.id()),
                Criteria.where("state").is("SCHEDULED"),
                Criteria.where("revision").is(schedule.revision()),
                Criteria.where("leaseOwner").is(workerId),
                Criteria.where("version").is(schedule.version())
        ));
    }

    private static Update published(
            String revision,
            Instant publishedAt
    ) {
        return new Update()
                .set("workflowStatus", "PUBLISHED")
                .set("currentPublishedRevision", revision)
                .set("publishedAt", publishedAt)
                .set("updatedAt", publishedAt)
                .unset("scheduledAt")
                .inc("version", 1);
    }
}
