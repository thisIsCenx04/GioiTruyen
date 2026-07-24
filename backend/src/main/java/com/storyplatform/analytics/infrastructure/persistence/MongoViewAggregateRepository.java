package com.storyplatform.analytics.infrastructure.persistence;

import com.storyplatform.analytics.application.ViewAggregateOperations;
import com.storyplatform.analytics.application.port.ViewAggregateRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class MongoViewAggregateRepository
        implements ViewAggregateRepository {

    public static final String COLLECTION = "reading_view_aggregates";
    public static final String AGGREGATE_VERSION = "view-aggregate-2026.1";
    private static final DateTimeFormatter HOUR =
            DateTimeFormatter.ofPattern("yyyyMMddHH")
                    .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("yyyyMMdd")
                    .withZone(ZoneOffset.UTC);
    private final MongoTemplate mongo;

    public MongoViewAggregateRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public Optional<Candidate> claim(
            String workerId,
            Instant now,
            Instant leaseUntil
    ) {
        AggregateCandidateDocument claimed = mongo.findAndModify(
                Query.query(new Criteria().andOperator(
                                Criteria.where("fraudRuleVersion")
                                        .exists(true),
                                Criteria.where("fraudDecision")
                                        .exists(true),
                                Criteria.where("aggregateVersion")
                                        .exists(false),
                                new Criteria().orOperator(
                                        Criteria.where("aggregateLeaseUntil")
                                                .exists(false),
                                        Criteria.where("aggregateLeaseUntil")
                                                .lte(now)
                                ),
                                new Criteria().orOperator(
                                        Criteria.where("aggregateRetryAt")
                                                .exists(false),
                                        Criteria.where("aggregateRetryAt")
                                                .lte(now)
                                )
                        ))
                        .with(Sort.by(
                                Sort.Order.asc("occurredAt"),
                                Sort.Order.asc("_id")
                        )),
                new Update()
                        .set("aggregateLeaseOwner", workerId)
                        .set("aggregateLeaseUntil", leaseUntil),
                FindAndModifyOptions.options().returnNew(true),
                AggregateCandidateDocument.class,
                MongoReadingViewValidationRepository
                        .CLASSIFICATION_COLLECTION
        );
        return Optional.ofNullable(claimed).map(value -> new Candidate(
                value.id(),
                value.storyId(),
                value.kind(),
                value.occurredAt(),
                value.valid(),
                value.reasons() == null ? Set.of() : value.reasons(),
                value.fraudDecision()
        ));
    }

    @Override
    public boolean commit(
            Candidate candidate,
            String workerId,
            Delta delta,
            Instant now
    ) {
        increment(candidate, delta, now, "HOUR");
        increment(candidate, delta, now, "DAY");
        return mongo.updateFirst(
                lease(candidate, workerId),
                new Update()
                        .set("aggregateVersion", AGGREGATE_VERSION)
                        .set("aggregatedAt", now)
                        .unset("aggregateLeaseOwner")
                        .unset("aggregateLeaseUntil")
                        .unset("aggregateRetryAt"),
                MongoReadingViewValidationRepository
                        .CLASSIFICATION_COLLECTION
        ).getModifiedCount() == 1;
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
                        .set("aggregateRetryAt", retryAt)
                        .unset("aggregateLeaseOwner")
                        .unset("aggregateLeaseUntil"),
                MongoReadingViewValidationRepository
                        .CLASSIFICATION_COLLECTION
        );
    }

    @Override
    public void reset(String storyId, Instant from, Instant to) {
        mongo.remove(
                Query.query(new Criteria().andOperator(
                        Criteria.where("storyId").is(storyId),
                        Criteria.where("bucketStart").gte(from).lt(to)
                )),
                COLLECTION
        );
        mongo.updateMulti(
                classificationRange(storyId, from, to),
                new Update()
                        .unset("aggregateVersion")
                        .unset("aggregatedAt")
                        .unset("aggregateLeaseOwner")
                        .unset("aggregateLeaseUntil")
                        .unset("aggregateRetryAt"),
                MongoReadingViewValidationRepository
                        .CLASSIFICATION_COLLECTION
        );
    }

    @Override
    public ViewAggregateOperations.Reconciliation reconcile(
            String storyId,
            Instant from,
            Instant to
    ) {
        Query base = classificationRange(storyId, from, to);
        long raw = mongo.count(
                base,
                MongoReadingViewValidationRepository
                        .CLASSIFICATION_COLLECTION
        );
        long completed = mongo.count(
                classificationRange(storyId, from, to)
                        .addCriteria(Criteria.where("kind").is("COMPLETION")),
                MongoReadingViewValidationRepository
                        .CLASSIFICATION_COLLECTION
        );
        long valid = mongo.count(
                classificationRange(storyId, from, to)
                        .addCriteria(
                                Criteria.where("kind").is("COMPLETION")
                        )
                        .addCriteria(Criteria.where("valid").is(true))
                        .addCriteria(
                                Criteria.where("fraudDecision").is("PASS")
                        ),
                MongoReadingViewValidationRepository
                        .CLASSIFICATION_COLLECTION
        );
        List<AggregateDocument> hourly = mongo.find(
                Query.query(new Criteria().andOperator(
                        Criteria.where("storyId").is(storyId),
                        Criteria.where("period").is("HOUR"),
                        Criteria.where("bucketStart").gte(from).lt(to)
                )),
                AggregateDocument.class,
                COLLECTION
        );
        return new ViewAggregateOperations.Reconciliation(
                raw,
                sum(hourly, Metric.RAW),
                completed,
                sum(hourly, Metric.COMPLETED),
                valid,
                sum(hourly, Metric.VALID),
                completed - valid,
                sum(hourly, Metric.INVALID)
        );
    }

    private void increment(
            Candidate candidate,
            Delta delta,
            Instant now,
            String period
    ) {
        Instant start = "HOUR".equals(period)
                ? candidate.occurredAt().truncatedTo(ChronoUnit.HOURS)
                : candidate.occurredAt().truncatedTo(ChronoUnit.DAYS);
        String suffix = "HOUR".equals(period)
                ? HOUR.format(start)
                : DAY.format(start);
        Update update = new Update()
                .setOnInsert(
                        "_id",
                        candidate.storyId() + ":" + period + ":" + suffix
                )
                .setOnInsert("storyId", candidate.storyId())
                .setOnInsert("period", period)
                .setOnInsert("bucketStart", start)
                .set("aggregateVersion", AGGREGATE_VERSION)
                .inc("rawEvents", delta.rawEvents())
                .inc("completedViews", delta.completedViews())
                .inc("validViews", delta.validViews())
                .inc("invalidViews", delta.invalidViews())
                .set("updatedAt", now);
        delta.reasons().forEach(
                reason -> update.inc("reasonCounts." + reason, 1)
        );
        mongo.upsert(
                Query.query(Criteria.where("_id").is(
                        candidate.storyId()
                                + ":" + period + ":" + suffix
                )),
                update,
                COLLECTION
        );
    }

    private static Query lease(Candidate candidate, String workerId) {
        return Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(candidate.id()),
                Criteria.where("aggregateVersion").exists(false),
                Criteria.where("aggregateLeaseOwner").is(workerId)
        ));
    }

    private static Query classificationRange(
            String storyId,
            Instant from,
            Instant to
    ) {
        return Query.query(new Criteria().andOperator(
                Criteria.where("storyId").is(storyId),
                Criteria.where("occurredAt").gte(from).lt(to),
                Criteria.where("fraudRuleVersion").exists(true)
        ));
    }

    private static long sum(
            List<AggregateDocument> values,
            Metric metric
    ) {
        return values.stream().mapToLong(value -> switch (metric) {
            case RAW -> value.rawEvents();
            case COMPLETED -> value.completedViews();
            case VALID -> value.validViews();
            case INVALID -> value.invalidViews();
        }).sum();
    }

    private enum Metric {
        RAW,
        COMPLETED,
        VALID,
        INVALID
    }

    public record AggregateCandidateDocument(
            @Id String id,
            String storyId,
            String kind,
            Instant occurredAt,
            boolean valid,
            Set<String> reasons,
            String fraudDecision
    ) {
    }

    public record AggregateDocument(
            @Id String id,
            String storyId,
            String period,
            Instant bucketStart,
            long rawEvents,
            long completedViews,
            long validViews,
            long invalidViews
    ) {
    }
}
