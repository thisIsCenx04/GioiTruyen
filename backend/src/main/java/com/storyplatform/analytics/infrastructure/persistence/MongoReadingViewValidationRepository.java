package com.storyplatform.analytics.infrastructure.persistence;

import com.storyplatform.analytics.application.RawReadingEvent;
import com.storyplatform.analytics.application.ReadingViewClassifier;
import com.storyplatform.analytics.application.port
        .ReadingViewValidationRepository;
import com.storyplatform.reading.application.contract
        .ReadingActorReferences;
import org.bson.Document;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class MongoReadingViewValidationRepository
        implements ReadingViewValidationRepository {

    public static final String CLASSIFICATION_COLLECTION =
            "reading_view_classifications";
    public static final String FINGERPRINT_COLLECTION =
            "reading_view_fingerprints";
    private final MongoTemplate mongo;
    private final ReadingActorReferences actorReferences;
    private final Duration fingerprintRetention;

    public MongoReadingViewValidationRepository(
            MongoTemplate mongo,
            ReadingActorReferences actorReferences,
            Duration fingerprintRetention
    ) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
        this.actorReferences = Objects.requireNonNull(
                actorReferences,
                "actorReferences"
        );
        if (fingerprintRetention == null
                || fingerprintRetention.compareTo(Duration.ofDays(7)) < 0
                || fingerprintRetention.compareTo(
                        Duration.ofDays(365)
                ) > 0) {
            throw new IllegalArgumentException(
                    "fingerprint retention must be between 7 and 365 days"
            );
        }
        this.fingerprintRetention = fingerprintRetention;
    }

    @Override
    public Optional<ClaimedBucket> claim(
            String workerId,
            Instant readyBefore,
            Instant now,
            Instant leaseUntil
    ) {
        Document filter = new Document("$and", List.of(
                new Document("bucketStart", new Document("$lte", readyBefore)),
                new Document("$expr", new Document("$gt", List.of(
                        "$eventCount",
                        new Document("$ifNull", List.of(
                                "$validatedCount", 0
                        ))
                ))),
                new Document("$or", List.of(
                        new Document(
                                "validationLeaseUntil",
                                new Document("$exists", false)
                        ),
                        new Document(
                                "validationLeaseUntil",
                                new Document("$lte", now)
                        )
                )),
                new Document("$or", List.of(
                        new Document(
                                "nextValidationAt",
                                new Document("$exists", false)
                        ),
                        new Document(
                                "nextValidationAt",
                                new Document("$lte", now)
                        )
                ))
        ));
        BucketDocument bucket = mongo.findAndModify(
                new BasicQuery(filter).with(Sort.by(
                        Sort.Order.asc("bucketStart"),
                        Sort.Order.asc("_id")
                )),
                new Update()
                        .set("validationLeaseOwner", workerId)
                        .set("validationLeaseUntil", leaseUntil)
                        .set("validationUpdatedAt", now),
                FindAndModifyOptions.options().returnNew(true),
                BucketDocument.class,
                MongoRawReadingEventRepository.COLLECTION
        );
        return Optional.ofNullable(bucket).map(
                MongoReadingViewValidationRepository::claimed
        );
    }

    @Override
    public boolean claimFingerprint(
            String fingerprint,
            String eventId,
            Instant now
    ) {
        FingerprintDocument existing = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(fingerprint)),
                new Update()
                        .setOnInsert("_id", fingerprint)
                        .setOnInsert("firstEventId", eventId)
                        .setOnInsert("firstSeenAt", now)
                        .setOnInsert(
                                "expiresAt",
                                now.plus(fingerprintRetention)
                        ),
                FindAndModifyOptions.options()
                        .upsert(true)
                        .returnNew(false),
                FingerprintDocument.class,
                FINGERPRINT_COLLECTION
        );
        return existing != null
                && !eventId.equals(existing.firstEventId());
    }

    @Override
    public boolean isSelfView(RawReadingEvent event) {
        StoryTeamProjection story = mongo.findById(
                event.storyId(),
                StoryTeamProjection.class,
                "stories"
        );
        if (story == null || story.teamId() == null) {
            return false;
        }
        List<MemberProjection> members = mongo.find(
                Query.query(new Criteria().andOperator(
                        Criteria.where("teamId").is(story.teamId()),
                        Criteria.where("state").is("ACTIVE")
                )),
                MemberProjection.class,
                "team_memberships"
        );
        return members.stream()
                .map(MemberProjection::userId)
                .filter(Objects::nonNull)
                .map(actorReferences::authenticatedUser)
                .anyMatch(event.actorRef()::equals);
    }

    @Override
    public Set<String> botSignals(
            RawReadingEvent event,
            RawReadingEvent previous
    ) {
        if (previous == null) {
            return Set.of();
        }
        Set<String> signals = new LinkedHashSet<>();
        Duration cadence = Duration.between(
                previous.occurredAt(),
                event.occurredAt()
        );
        if (cadence.isZero() || cadence.isNegative()) {
            signals.add("EVENT_TIME_REGRESSION");
        }
        double progress = event.position() - previous.position();
        if (!cadence.isNegative()
                && cadence.compareTo(Duration.ofSeconds(5)) <= 0
                && progress >= 40) {
            signals.add("IMPOSSIBLE_PROGRESS");
        }
        if (event.activeSeconds() != null
                && !cadence.isNegative()
                && event.activeSeconds() > cadence.toSeconds() + 2) {
            signals.add("ACTIVE_TIME_EXCEEDS_CADENCE");
        }
        return Set.copyOf(signals);
    }

    @Override
    public void save(
            List<ReadingViewClassifier.Classification> classifications
    ) {
        if (classifications.isEmpty()) {
            return;
        }
        BulkOperations bulk = mongo.bulkOps(
                BulkOperations.BulkMode.UNORDERED,
                ClassificationDocument.class,
                CLASSIFICATION_COLLECTION
        );
        classifications.forEach(classification -> bulk.upsert(
                Query.query(Criteria.where("_id").is(
                        classification.eventId()
                                + ":" + classification.ruleVersion()
                )),
                new Update()
                        .setOnInsert("eventId", classification.eventId())
                        .setOnInsert(
                                "fingerprint",
                                classification.fingerprint()
                        )
                        .setOnInsert(
                                "sessionRef",
                                classification.sessionRef()
                        )
                        .setOnInsert(
                                "actorRef",
                                classification.actorRef()
                        )
                        .setOnInsert("storyId", classification.storyId())
                        .setOnInsert(
                                "chapterId",
                                classification.chapterId()
                        )
                        .setOnInsert(
                                "kind",
                                classification.kind().name()
                        )
                        .setOnInsert(
                                "occurredAt",
                                classification.occurredAt()
                        )
                        .setOnInsert(
                                "ruleVersion",
                                classification.ruleVersion()
                        )
                        .setOnInsert("valid", classification.valid())
                        .setOnInsert("reasons", classification.reasons())
                        .setOnInsert(
                                "classifiedAt",
                                classification.classifiedAt()
                        )
        ));
        bulk.execute();
    }

    @Override
    public boolean complete(
            ClaimedBucket bucket,
            String workerId,
            Instant now
    ) {
        return mongo.updateFirst(
                leaseQuery(bucket, workerId),
                new Update()
                        .set("validatedCount", bucket.snapshotCount())
                        .set("validationUpdatedAt", now)
                        .unset("validationLeaseOwner")
                        .unset("validationLeaseUntil")
                        .unset("nextValidationAt")
                        .unset("validationFailureCode"),
                MongoRawReadingEventRepository.COLLECTION
        ).getModifiedCount() == 1;
    }

    @Override
    public void retry(
            ClaimedBucket bucket,
            String workerId,
            Instant retryAt,
            String failureCode
    ) {
        mongo.updateFirst(
                leaseQuery(bucket, workerId),
                new Update()
                        .set("nextValidationAt", retryAt)
                        .set("validationFailureCode", failureCode)
                        .unset("validationLeaseOwner")
                        .unset("validationLeaseUntil"),
                MongoRawReadingEventRepository.COLLECTION
        );
    }

    private static Query leaseQuery(
            ClaimedBucket bucket,
            String workerId
    ) {
        return Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(bucket.id()),
                Criteria.where("validationLeaseOwner").is(workerId),
                new Criteria().orOperator(
                        Criteria.where("validatedCount")
                                .is(bucket.validatedCount()),
                        Criteria.where("validatedCount").exists(false)
                )
        ));
    }

    private static ClaimedBucket claimed(BucketDocument bucket) {
        List<MongoRawReadingEventRepository.EmbeddedEvent> values =
                bucket.events() == null ? List.of() : bucket.events();
        int from = Math.max(bucket.validatedCount(), 0);
        int snapshot = Math.min(bucket.eventCount(), values.size());
        if (from >= snapshot) {
            throw new IllegalStateException(
                    "raw reading event bucket counts are inconsistent"
            );
        }
        List<RawReadingEvent> events = new ArrayList<>(snapshot - from);
        for (int index = from; index < snapshot; index++) {
            events.add(raw(values.get(index)));
        }
        return new ClaimedBucket(
                bucket.id(),
                from,
                snapshot,
                events
        );
    }

    private static RawReadingEvent raw(
            MongoRawReadingEventRepository.EmbeddedEvent event
    ) {
        return new RawReadingEvent(
                event.eventId(),
                RawReadingEvent.Kind.valueOf(event.kind()),
                event.sessionRef(),
                event.actorRef(),
                event.storyId(),
                event.chapterId(),
                event.sequence(),
                event.occurredAt(),
                event.position(),
                event.activeSeconds(),
                event.receivedAt()
        );
    }

    public record BucketDocument(
            @Id String id,
            Instant bucketStart,
            int eventCount,
            int validatedCount,
            List<MongoRawReadingEventRepository.EmbeddedEvent> events
    ) {
    }

    public record FingerprintDocument(
            @Id String fingerprint,
            String firstEventId,
            Instant firstSeenAt,
            Instant expiresAt
    ) {
    }

    public record ClassificationDocument(
            @Id String id,
            String eventId,
            String fingerprint,
            String sessionRef,
            String actorRef,
            String storyId,
            String chapterId,
            String kind,
            Instant occurredAt,
            String ruleVersion,
            boolean valid,
            Set<String> reasons,
            Instant classifiedAt
    ) {
    }

    public record StoryTeamProjection(String id, String teamId) {
    }

    public record MemberProjection(String id, String userId) {
    }
}
