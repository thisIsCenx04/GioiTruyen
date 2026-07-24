package com.storyplatform.moderation.infrastructure.persistence;

import com.storyplatform.moderation.application.ModerationDecisionOperations;
import com.storyplatform.moderation.application.port
        .ModerationDecisionRepository;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class MongoModerationDecisionRepository
        implements ModerationDecisionRepository {

    private final MongoTemplate mongo;
    private final Supplier<String> identifiers;

    public MongoModerationDecisionRepository(
            MongoTemplate mongo,
            Supplier<String> identifiers
    ) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
        this.identifiers = Objects.requireNonNull(
                identifiers,
                "identifiers"
        );
    }

    @Override
    public boolean hasBlockingDonationPolicy(String reviewId) {
        Criteria blocked = Criteria.where("checks").elemMatch(
                Criteria.where("rule").is("QR_POLICY")
                        .and("outcome").is("FAIL")
                        .and("code").is(
                                "EXTERNAL_DONATION_BLOCKED"
                        )
        );
        return mongo.exists(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(reviewId),
                        blocked
                )),
                MongoModerationReviewDocument.class
        );
    }

    @Override
    public Result decide(
            String reviewId,
            String reviewerId,
            long expectedVersion,
            DecisionRecord decision,
            Instant now
    ) {
        String reviewState = reviewState(decision.decision());
        var decisionDocument =
                new MongoModerationReviewDocument
                        .ModerationDecisionDocument(
                        decision.decision().name(),
                        decision.reasonCode(),
                        decision.note(),
                        decision.evidenceRefs(),
                        decision.policyVersion(),
                        reviewerId,
                        now
                );
        MongoModerationReviewDocument review = mongo.findAndModify(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(reviewId),
                        Criteria.where("state").is("CLAIMED"),
                        Criteria.where("assigneeId").is(reviewerId),
                        Criteria.where("leaseUntil").gt(now),
                        Criteria.where("version").is(expectedVersion),
                        Criteria.where("decision").exists(false)
                )),
                new Update()
                        .set("state", reviewState)
                        .set("decision", decisionDocument)
                        .set("decidedAt", now)
                        .set("updatedAt", now)
                        .unset("leaseUntil")
                        .inc("version", 1),
                FindAndModifyOptions.options().returnNew(true),
                MongoModerationReviewDocument.class
        );
        if (review == null) {
            return new Result(Outcome.CONFLICT, null, expectedVersion);
        }
        String targetState = targetState(decision.decision());
        var story = mongo.updateFirst(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(review.targetId()),
                        Criteria.where("teamId").is(review.teamId()),
                        Criteria.where("workflowStatus").is("IN_REVIEW"),
                        Criteria.where("currentRevision").is(
                                review.submittedRevision()
                        )
                )),
                new Update()
                        .set("workflowStatus", targetState)
                        .set("updatedAt", now)
                        .inc("version", 1),
                MongoModerationStoryDocument.class
        );
        if (story.getModifiedCount() != 1) {
            return new Result(
                    Outcome.STALE_TARGET,
                    reviewState,
                    review.version()
            );
        }
        List<MongoModerationReviewDocument.FrozenChapterRevision> chapters =
                review.chapterRevisions() == null
                        ? List.of()
                        : review.chapterRevisions();
        if (chapters.isEmpty()) {
            return new Result(
                    Outcome.STALE_TARGET,
                    reviewState,
                    review.version()
            );
        }
        Criteria[] frozen = chapters.stream()
                .map(value -> new Criteria().andOperator(
                        Criteria.where("_id").is(value.chapterId()),
                        Criteria.where("currentRevision").is(
                                value.revisionId()
                        )
                ))
                .toArray(Criteria[]::new);
        var chapterUpdate = mongo.updateMulti(
                Query.query(new Criteria().andOperator(
                        Criteria.where("storyId").is(review.targetId()),
                        Criteria.where("teamId").is(review.teamId()),
                        Criteria.where("workflowStatus").is("DRAFT"),
                        new Criteria().orOperator(frozen)
                )),
                new Update()
                        .set("workflowStatus", targetState)
                        .set("updatedAt", now)
                        .inc("version", 1),
                MongoModerationChapterDocument.class
        );
        if (chapterUpdate.getModifiedCount() != chapters.size()) {
            return new Result(
                    Outcome.STALE_TARGET,
                    reviewState,
                    review.version()
            );
        }
        mongo.insert(new MongoModerationAuditDocument(
                identifiers.get(),
                "moderation.review.decided",
                reviewerId,
                "STORY",
                review.targetId(),
                review.teamId(),
                decision.decision().name(),
                decision.reasonCode(),
                decision.policyVersion(),
                decision.evidenceRefs(),
                now
        ));
        return new Result(
                Outcome.SUCCESS,
                reviewState,
                review.version()
        );
    }

    private static String reviewState(
            ModerationDecisionOperations.Decision decision
    ) {
        return switch (decision) {
            case APPROVE -> "APPROVED";
            case REQUEST_CHANGES -> "CHANGES_REQUESTED";
            case REJECT -> "REJECTED";
        };
    }

    private static String targetState(
            ModerationDecisionOperations.Decision decision
    ) {
        return switch (decision) {
            case APPROVE -> "APPROVED";
            case REQUEST_CHANGES -> "CHANGES_REQUESTED";
            case REJECT -> "ARCHIVED";
        };
    }
}
