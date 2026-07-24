package com.storyplatform.moderation.infrastructure.persistence;

import com.storyplatform.moderation.application.ModerationQueueOperations;
import com.storyplatform.moderation.application.port
        .ModerationQueueCursorCodec;
import com.storyplatform.moderation.application.port
        .ModerationQueueRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class MongoModerationQueueRepository
        implements ModerationQueueRepository {

    private final MongoTemplate mongo;

    public MongoModerationQueueRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public List<ModerationQueueOperations.ReviewCase> findClaimable(
            ModerationQueueCursorCodec.Cursor after,
            int limit,
            Instant now
    ) {
        Criteria criteria = claimable(now);
        if (after != null) {
            criteria = new Criteria().andOperator(
                    criteria,
                    after(after)
            );
        }
        Query query = Query.query(criteria)
                .with(Sort.by(
                        Sort.Order.desc("priority"),
                        Sort.Order.asc("submittedAt"),
                        Sort.Order.asc("_id")
                ))
                .limit(limit);
        return mongo.find(
                        query,
                        MongoModerationReviewDocument.class
                ).stream()
                .map(MongoModerationQueueRepository::view)
                .toList();
    }

    @Override
    public Optional<ModerationQueueOperations.ReviewCase> claim(
            String reviewId,
            String reviewerId,
            long expectedVersion,
            Instant now,
            Instant leaseUntil
    ) {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(reviewId),
                Criteria.where("version").is(expectedVersion),
                claimable(now)
        ));
        MongoModerationReviewDocument claimed = mongo.findAndModify(
                query,
                new Update()
                        .set("state", "CLAIMED")
                        .set("assigneeId", reviewerId)
                        .set("claimedAt", now)
                        .set("leaseUntil", leaseUntil)
                        .set("updatedAt", now)
                        .inc("version", 1),
                FindAndModifyOptions.options().returnNew(true),
                MongoModerationReviewDocument.class
        );
        return Optional.ofNullable(claimed).map(
                MongoModerationQueueRepository::view
        );
    }

    @Override
    public Optional<ModerationQueueOperations.ReviewDetail> find(
            String reviewId
    ) {
        MongoModerationReviewDocument review = mongo.findById(
                reviewId,
                MongoModerationReviewDocument.class
        );
        if (review == null || review.chapterRevisions() == null) {
            return Optional.empty();
        }
        StoryRevisionProjection story = mongo.findById(
                review.submittedRevision(),
                StoryRevisionProjection.class,
                "story_revisions"
        );
        if (story == null || story.snapshot() == null) {
            return Optional.empty();
        }
        List<String> revisionIds = review.chapterRevisions().stream()
                .map(MongoModerationReviewDocument
                        .FrozenChapterRevision::revisionId)
                .toList();
        Map<String, ChapterRevisionProjection> revisions = mongo.find(
                        Query.query(Criteria.where("_id").in(revisionIds)),
                        ChapterRevisionProjection.class,
                        "chapter_revisions"
                ).stream()
                .collect(Collectors.toMap(
                        ChapterRevisionProjection::id,
                        Function.identity()
                ));
        if (revisions.size() != revisionIds.size()) {
            return Optional.empty();
        }
        List<ModerationQueueOperations.ChapterEvidence> chapters =
                review.chapterRevisions().stream()
                        .map(frozen -> chapter(
                                frozen,
                                revisions.get(frozen.revisionId())
                        ))
                        .toList();
        StorySnapshotProjection snapshot = story.snapshot();
        return Optional.of(new ModerationQueueOperations.ReviewDetail(
                view(review),
                new ModerationQueueOperations.StoryEvidence(
                        story.id(),
                        story.revisionNo(),
                        snapshot.title(),
                        snapshot.synopsis(),
                        snapshot.origin(),
                        snapshot.language(),
                        snapshot.categoryIds() == null
                                ? List.of()
                                : snapshot.categoryIds(),
                        snapshot.coverAssetId(),
                        story.checksum()
                ),
                chapters
        ));
    }

    private static Criteria claimable(Instant now) {
        return new Criteria().andOperator(
                Criteria.where("targetType").is("STORY"),
                new Criteria().orOperator(
                        Criteria.where("state").is("OPEN"),
                        new Criteria().andOperator(
                                Criteria.where("state").is("CLAIMED"),
                                Criteria.where("leaseUntil").lt(now)
                        )
                )
        );
    }

    private static Criteria after(
            ModerationQueueCursorCodec.Cursor cursor
    ) {
        return new Criteria().orOperator(
                Criteria.where("priority").lt(cursor.priority()),
                new Criteria().andOperator(
                        Criteria.where("priority").is(cursor.priority()),
                        Criteria.where("submittedAt").gt(
                                cursor.submittedAt()
                        )
                ),
                new Criteria().andOperator(
                        Criteria.where("priority").is(cursor.priority()),
                        Criteria.where("submittedAt").is(
                                cursor.submittedAt()
                        ),
                        Criteria.where("_id").gt(cursor.id())
                )
        );
    }

    private static ModerationQueueOperations.ReviewCase view(
            MongoModerationReviewDocument value
    ) {
        return new ModerationQueueOperations.ReviewCase(
                value.id(),
                value.targetType(),
                value.targetId(),
                value.teamId(),
                value.state(),
                value.priority(),
                value.manualFallback(),
                value.checks() == null ? List.of() : value.checks(),
                value.assigneeId(),
                value.leaseUntil(),
                value.submittedAt(),
                value.version()
        );
    }

    private static ModerationQueueOperations.ChapterEvidence chapter(
            MongoModerationReviewDocument.FrozenChapterRevision frozen,
            ChapterRevisionProjection revision
    ) {
        return new ModerationQueueOperations.ChapterEvidence(
                frozen.chapterId(),
                revision.id(),
                frozen.number(),
                revision.revisionNo(),
                revision.contentHtml(),
                revision.plainText(),
                revision.checksum()
        );
    }

    public record StoryRevisionProjection(
            @Id String id,
            long revisionNo,
            StorySnapshotProjection snapshot,
            String checksum
    ) {
    }

    public record StorySnapshotProjection(
            String title,
            String synopsis,
            String origin,
            String language,
            List<String> categoryIds,
            String coverAssetId
    ) {
    }

    public record ChapterRevisionProjection(
            @Id String id,
            long revisionNo,
            String contentHtml,
            String plainText,
            String checksum
    ) {
    }
}
