package com.storyplatform.moderation.infrastructure.persistence;

import com.storyplatform.moderation.application.ModerationQueueOperations;
import com.storyplatform.moderation.application.port
        .ModerationQueueCursorCodec;
import com.storyplatform.moderation.application.port
        .ModerationQueueRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
}
