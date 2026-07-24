package com.storyplatform.publishing.infrastructure.persistence;

import com.storyplatform.publishing.application.port
        .PublishingScheduleRepository;
import com.storyplatform.publishing.domain.PublishingSchedule;
import com.storyplatform.publishing.domain.PublishingReview;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MongoPublishingScheduleRepository
        implements PublishingScheduleRepository {

    private static final String STORIES = "stories";
    private static final String CHAPTERS = "chapters";
    private final MongoTemplate mongo;

    public MongoPublishingScheduleRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public Optional<PublishingSchedule> findActive(
            String teamId,
            String storyId
    ) {
        MongoPublishingScheduleDocument document = mongo.findOne(
                Query.query(new Criteria().andOperator(
                        Criteria.where("teamId").is(teamId),
                        Criteria.where("targetId").is(storyId),
                        Criteria.where("targetType").is("STORY"),
                        Criteria.where("state").is("SCHEDULED")
                )),
                MongoPublishingScheduleDocument.class
        );
        return Optional.ofNullable(document)
                .map(MongoPublishingScheduleDocument::toDomain);
    }

    @Override
    public Optional<ApprovedCandidate> findApproved(
            String teamId,
            String storyId,
            String revision
    ) {
        Document story = mongo.findOne(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(storyId),
                        Criteria.where("teamId").is(teamId),
                        Criteria.where("workflowStatus").is("APPROVED"),
                        Criteria.where("currentRevision").is(revision)
                )),
                Document.class,
                STORIES
        );
        if (story == null) {
            return Optional.empty();
        }
        MongoPublishingReviewDocument review = mongo.findOne(
                Query.query(new Criteria().andOperator(
                        Criteria.where("teamId").is(teamId),
                        Criteria.where("targetId").is(storyId),
                        Criteria.where("targetType").is("STORY"),
                        Criteria.where("submittedRevision").is(revision),
                        Criteria.where("state").is(
                                PublishingReview.State.APPROVED
                        )
                )),
                MongoPublishingReviewDocument.class
        );
        if (review == null || review.chapterRevisions() == null
                || review.chapterRevisions().isEmpty()) {
            return Optional.empty();
        }
        List<PublishingSchedule.FrozenChapterRevision> chapters =
                review.chapterRevisions().stream()
                        .map(value -> new PublishingSchedule
                                .FrozenChapterRevision(
                                value.chapterId(),
                                value.revisionId(),
                                value.number()
                        ))
                        .toList();
        Number version = story.get("version", Number.class);
        if (version == null || version.longValue() < 1) {
            return Optional.empty();
        }
        return Optional.of(new ApprovedCandidate(
                storyId,
                teamId,
                revision,
                version.longValue(),
                chapters
        ));
    }

    @Override
    public boolean create(
            ApprovedCandidate candidate,
            PublishingSchedule schedule
    ) {
        var story = mongo.updateFirst(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(candidate.storyId()),
                        Criteria.where("teamId").is(candidate.teamId()),
                        Criteria.where("workflowStatus").is("APPROVED"),
                        Criteria.where("currentRevision").is(
                                candidate.revision()
                        ),
                        Criteria.where("version").is(
                                candidate.storyVersion()
                        )
                )),
                scheduleTarget("SCHEDULED", schedule.publishAt(),
                        schedule.updatedAt()),
                STORIES
        );
        if (story.getModifiedCount() != 1) {
            return false;
        }
        if (!updateChapters(
                candidate.teamId(),
                candidate.storyId(),
                candidate.chapters(),
                "APPROVED",
                "SCHEDULED",
                schedule.publishAt(),
                schedule.updatedAt()
        )) {
            return false;
        }
        mongo.insert(MongoPublishingScheduleDocument.from(schedule));
        return true;
    }

    @Override
    public boolean reschedule(
            PublishingSchedule current,
            Instant publishAt,
            String timeZone,
            Instant updatedAt,
            long expectedVersion
    ) {
        var schedule = mongo.updateFirst(
                activeSchedule(current, expectedVersion),
                new Update()
                        .set("publishAt", publishAt)
                        .set("timeZone", timeZone)
                        .set("updatedAt", updatedAt)
                        .inc("version", 1),
                MongoPublishingScheduleDocument.class
        );
        if (schedule.getModifiedCount() != 1) {
            return false;
        }
        var story = mongo.updateFirst(
                pinnedStory(current, "SCHEDULED"),
                new Update()
                        .set("scheduledAt", publishAt)
                        .set("updatedAt", updatedAt),
                STORIES
        );
        if (story.getMatchedCount() != 1) {
            return false;
        }
        return touchChapters(
                current,
                "SCHEDULED",
                new Update()
                        .set("scheduledAt", publishAt)
                        .set("updatedAt", updatedAt)
        );
    }

    @Override
    public boolean cancel(
            PublishingSchedule current,
            Instant updatedAt,
            long expectedVersion
    ) {
        var schedule = mongo.updateFirst(
                activeSchedule(current, expectedVersion),
                new Update()
                        .set("state", "CANCELLED")
                        .set("cancelledAt", updatedAt)
                        .set("updatedAt", updatedAt)
                        .inc("version", 1),
                MongoPublishingScheduleDocument.class
        );
        if (schedule.getModifiedCount() != 1) {
            return false;
        }
        var story = mongo.updateFirst(
                pinnedStory(current, "SCHEDULED"),
                new Update()
                        .set("workflowStatus", "APPROVED")
                        .unset("scheduledAt")
                        .set("updatedAt", updatedAt)
                        .inc("version", 1),
                STORIES
        );
        if (story.getModifiedCount() != 1) {
            return false;
        }
        return touchChapters(
                current,
                "SCHEDULED",
                new Update()
                        .set("workflowStatus", "APPROVED")
                        .unset("scheduledAt")
                        .set("updatedAt", updatedAt)
                        .inc("version", 1)
        );
    }

    private boolean updateChapters(
            String teamId,
            String storyId,
            List<PublishingSchedule.FrozenChapterRevision> chapters,
            String from,
            String to,
            Instant publishAt,
            Instant updatedAt
    ) {
        var result = mongo.updateMulti(
                pinnedChapters(teamId, storyId, chapters, from),
                scheduleTarget(to, publishAt, updatedAt),
                CHAPTERS
        );
        return result.getModifiedCount() == chapters.size();
    }

    private boolean touchChapters(
            PublishingSchedule current,
            String state,
            Update update
    ) {
        var result = mongo.updateMulti(
                pinnedChapters(
                        current.teamId(),
                        current.targetId(),
                        current.chapterRevisions(),
                        state
                ),
                update,
                CHAPTERS
        );
        return result.getMatchedCount()
                == current.chapterRevisions().size();
    }

    private static Query activeSchedule(
            PublishingSchedule current,
            long expectedVersion
    ) {
        return Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(current.id()),
                Criteria.where("teamId").is(current.teamId()),
                Criteria.where("targetId").is(current.targetId()),
                Criteria.where("revision").is(current.revision()),
                Criteria.where("state").is("SCHEDULED"),
                Criteria.where("version").is(expectedVersion)
        ));
    }

    private static Query pinnedStory(
            PublishingSchedule current,
            String state
    ) {
        return Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(current.targetId()),
                Criteria.where("teamId").is(current.teamId()),
                Criteria.where("workflowStatus").is(state),
                Criteria.where("currentRevision").is(current.revision())
        ));
    }

    private static Query pinnedChapters(
            String teamId,
            String storyId,
            List<PublishingSchedule.FrozenChapterRevision> chapters,
            String state
    ) {
        Criteria[] frozen = chapters.stream()
                .map(value -> new Criteria().andOperator(
                        Criteria.where("_id").is(value.chapterId()),
                        Criteria.where("currentRevision").is(
                                value.revisionId()
                        )
                ))
                .toArray(Criteria[]::new);
        return Query.query(new Criteria().andOperator(
                Criteria.where("teamId").is(teamId),
                Criteria.where("storyId").is(storyId),
                Criteria.where("workflowStatus").is(state),
                new Criteria().orOperator(frozen)
        ));
    }

    private static Update scheduleTarget(
            String state,
            Instant publishAt,
            Instant updatedAt
    ) {
        return new Update()
                .set("workflowStatus", state)
                .set("scheduledAt", publishAt)
                .set("updatedAt", updatedAt)
                .inc("version", 1);
    }
}
