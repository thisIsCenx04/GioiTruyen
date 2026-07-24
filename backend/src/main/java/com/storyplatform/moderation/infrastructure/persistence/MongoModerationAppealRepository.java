package com.storyplatform.moderation.infrastructure.persistence;

import com.storyplatform.moderation.application.ModerationAppealOperations;
import com.storyplatform.moderation.application.port.ModerationAppealRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class MongoModerationAppealRepository
        implements ModerationAppealRepository {

    private final MongoTemplate mongo;

    public MongoModerationAppealRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public Optional<EligibleReview> eligibleReview(
            String reviewId,
            String actorId
    ) {
        MongoModerationReviewDocument review = mongo.findOne(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(reviewId),
                        Criteria.where("state").in(
                                "REJECTED",
                                "CHANGES_REQUESTED"
                        ),
                        Criteria.where("decision.decidedAt").ne(null)
                )),
                MongoModerationReviewDocument.class
        );
        if (review == null || review.decision() == null) {
            return Optional.empty();
        }
        boolean member = mongo.exists(
                Query.query(new Criteria().andOperator(
                        Criteria.where("teamId").is(review.teamId()),
                        Criteria.where("userId").is(actorId),
                        Criteria.where("state").is("ACTIVE")
                )),
                "team_memberships"
        );
        if (!member) {
            return Optional.empty();
        }
        return Optional.of(new EligibleReview(
                review.id(),
                review.decision().reviewerId(),
                review.decision().decidedAt()
        ));
    }

    @Override
    public CreateResult createIfAbsent(
            String appealId,
            EligibleReview review,
            String actorId,
            String statement,
            Instant createdAt,
            Instant deadline
    ) {
        MongoModerationAppealDocument document =
                new MongoModerationAppealDocument(
                        appealId,
                        review.reviewId(),
                        actorId,
                        review.originalReviewerId(),
                        statement,
                        "PENDING",
                        null,
                        null,
                        null,
                        null,
                        createdAt,
                        deadline,
                        null
                );
        try {
            mongo.insert(document);
            return new CreateResult(Outcome.SUCCESS, view(document));
        } catch (DuplicateKeyException exception) {
            MongoModerationAppealDocument existing = mongo.findOne(
                    Query.query(Criteria.where("reviewId").is(
                            review.reviewId()
                    )),
                    MongoModerationAppealDocument.class
            );
            return new CreateResult(
                    Outcome.DUPLICATE,
                    existing == null ? null : view(existing)
            );
        }
    }

    @Override
    public ResolveResult resolve(
            String reviewId,
            String appealId,
            String reviewerId,
            ModerationAppealOperations.AppealDecision decision,
            String reasonCode,
            String note,
            Instant decidedAt
    ) {
        String finalStatus = decision
                == ModerationAppealOperations.AppealDecision.UPHOLD
                ? "UPHELD"
                : "OVERTURNED";
        MongoModerationAppealDocument updated = mongo.findAndModify(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(appealId),
                        Criteria.where("reviewId").is(reviewId),
                        Criteria.where("status").is("PENDING"),
                        Criteria.where("originalReviewerId").ne(reviewerId),
                        Criteria.where("appellantId").ne(reviewerId)
                )),
                new Update()
                        .set("status", finalStatus)
                        .set("decision", decision.name())
                        .set("decisionReasonCode", reasonCode)
                        .set("decisionNote", note)
                        .set("appealReviewerId", reviewerId)
                        .set("decidedAt", decidedAt),
                FindAndModifyOptions.options().returnNew(true),
                MongoModerationAppealDocument.class
        );
        if (updated == null) {
            return new ResolveResult(Outcome.CONFLICT, null);
        }
        var reviewUpdate = mongo.updateFirst(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(reviewId),
                        Criteria.where("state").in(
                                "REJECTED",
                                "CHANGES_REQUESTED"
                        ),
                        Criteria.where("appealFinalStatus").exists(false)
                )),
                new Update()
                        .set("appealFinalStatus", finalStatus)
                        .set("appealId", appealId)
                        .set("appealDecidedAt", decidedAt)
                        .inc("version", 1),
                MongoModerationReviewDocument.class
        );
        if (reviewUpdate.getModifiedCount() != 1) {
            return new ResolveResult(Outcome.CONFLICT, null);
        }
        return new ResolveResult(Outcome.SUCCESS, view(updated));
    }

    private static ModerationAppealOperations.AppealView view(
            MongoModerationAppealDocument value
    ) {
        return new ModerationAppealOperations.AppealView(
                value.id(),
                value.reviewId(),
                value.appellantId(),
                value.originalReviewerId(),
                value.statement(),
                value.status(),
                value.decision() == null
                        ? null
                        : ModerationAppealOperations.AppealDecision.valueOf(
                                value.decision()
                        ),
                value.decisionReasonCode(),
                value.decisionNote(),
                value.appealReviewerId(),
                value.createdAt(),
                value.deadline(),
                value.decidedAt()
        );
    }
}
