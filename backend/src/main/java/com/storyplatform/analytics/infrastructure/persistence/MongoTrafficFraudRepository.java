package com.storyplatform.analytics.infrastructure.persistence;

import com.storyplatform.analytics.application.TrafficFraudScorer;
import com.storyplatform.analytics.application.port.TrafficFraudRepository;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class MongoTrafficFraudRepository
        implements TrafficFraudRepository {

    public static final String CASE_COLLECTION = "traffic_review_cases";
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);
    private final MongoTemplate mongo;

    public MongoTrafficFraudRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public Optional<Candidate> claim(
            String workerId,
            Instant now,
            Instant leaseUntil
    ) {
        FraudCandidateDocument claimed = mongo.findAndModify(
                Query.query(new Criteria().andOperator(
                                Criteria.where("fraudRuleVersion")
                                        .exists(false),
                                new Criteria().orOperator(
                                        Criteria.where("fraudLeaseUntil")
                                                .exists(false),
                                        Criteria.where("fraudLeaseUntil")
                                                .lte(now)
                                ),
                                new Criteria().orOperator(
                                        Criteria.where("fraudRetryAt")
                                                .exists(false),
                                        Criteria.where("fraudRetryAt")
                                                .lte(now)
                                )
                        ))
                        .with(Sort.by(
                                Sort.Order.asc("classifiedAt"),
                                Sort.Order.asc("_id")
                        )),
                new Update()
                        .set("fraudLeaseOwner", workerId)
                        .set("fraudLeaseUntil", leaseUntil),
                FindAndModifyOptions.options().returnNew(true),
                FraudCandidateDocument.class,
                MongoReadingViewValidationRepository
                        .CLASSIFICATION_COLLECTION
        );
        return Optional.ofNullable(claimed).map(value -> new Candidate(
                value.id(),
                value.eventId(),
                value.actorRef(),
                value.storyId(),
                value.reasons() == null ? Set.of() : value.reasons()
        ));
    }

    @Override
    public boolean commit(
            Candidate candidate,
            String workerId,
            TrafficFraudScorer.Score score
    ) {
        boolean completed = mongo.updateFirst(
                lease(candidate, workerId),
                new Update()
                        .set("fraudRuleVersion", score.ruleVersion())
                        .set("fraudScore", score.value())
                        .set("fraudDecision", score.decision().name())
                        .set(
                                "fraudContributions",
                                score.contributions()
                        )
                        .set("fraudScoredAt", score.scoredAt())
                        .unset("fraudLeaseOwner")
                        .unset("fraudLeaseUntil")
                        .unset("fraudRetryAt"),
                MongoReadingViewValidationRepository
                        .CLASSIFICATION_COLLECTION
        ).getModifiedCount() == 1;
        if (completed
                && score.decision() != TrafficFraudScorer.Decision.PASS) {
            openReviewCase(candidate, score);
        }
        return completed;
    }

    private void openReviewCase(
            Candidate candidate,
            TrafficFraudScorer.Score score
    ) {
        String id = candidate.actorRef()
                + ":" + candidate.storyId()
                + ":" + DAY.format(score.scoredAt());
        Update update = new Update()
                .setOnInsert("_id", id)
                .setOnInsert("actorRef", candidate.actorRef())
                .setOnInsert("storyId", candidate.storyId())
                .setOnInsert("openedAt", score.scoredAt())
                .set("ruleVersion", score.ruleVersion())
                .max("maximumScore", score.value())
                .inc("eventCount", 1)
                .set("updatedAt", score.scoredAt());
        if (score.decision()
                == TrafficFraudScorer.Decision.HOLD_FOR_REVIEW) {
            update.set("state", "HOLD_FOR_REVIEW");
        } else {
            update.setOnInsert("state", "OPEN");
        }
        score.contributions().keySet().forEach(
                signal -> update.addToSet("signals", signal)
        );
        mongo.upsert(
                Query.query(Criteria.where("_id").is(id)),
                update,
                CASE_COLLECTION
        );
    }

    @Override
    public void retry(
            Candidate candidate,
            String workerId,
            Instant retryAt
    ) {
        mongo.updateFirst(
                lease(candidate, workerId),
                new Update()
                        .set("fraudRetryAt", retryAt)
                        .unset("fraudLeaseOwner")
                        .unset("fraudLeaseUntil"),
                MongoReadingViewValidationRepository
                        .CLASSIFICATION_COLLECTION
        );
    }

    private static Query lease(Candidate candidate, String workerId) {
        return Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(candidate.id()),
                Criteria.where("fraudRuleVersion").exists(false),
                Criteria.where("fraudLeaseOwner").is(workerId)
        ));
    }

    public record FraudCandidateDocument(
            @Id String id,
            String eventId,
            String actorRef,
            String storyId,
            Set<String> reasons,
            Instant classifiedAt
    ) {
    }
}
