package com.storyplatform.analytics.infrastructure.persistence;

import com.storyplatform.analytics.application.RawReadingEvent;
import com.storyplatform.analytics.application.port
        .RawReadingEventRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MongoRawReadingEventRepository
        implements RawReadingEventRepository {

    public static final String COLLECTION = "raw_reading_event_buckets";
    private static final int MAXIMUM_APPEND_SIZE = 20;
    private final MongoTemplate mongo;
    private final Duration retention;

    public MongoRawReadingEventRepository(
            MongoTemplate mongo,
            Duration retention
    ) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
        if (retention == null
                || retention.compareTo(Duration.ofDays(7)) < 0
                || retention.compareTo(Duration.ofDays(365)) > 0) {
            throw new IllegalArgumentException(
                    "raw event retention must be between 7 and 365 days"
            );
        }
        this.retention = retention;
    }

    @Override
    public void append(List<RawReadingEvent> values) {
        if (values == null
                || values.isEmpty()
                || values.size() > MAXIMUM_APPEND_SIZE
                || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "raw event append must contain between 1 and 20 events"
            );
        }
        Map<BucketKey, List<RawReadingEvent>> groups = values.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        MongoRawReadingEventRepository::bucket,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        groups.forEach(this::appendBucket);
    }

    private void appendBucket(
            BucketKey key,
            List<RawReadingEvent> events
    ) {
        Object[] embedded = events.stream()
                .map(MongoRawReadingEventRepository::embedded)
                .toArray();
        mongo.upsert(
                Query.query(Criteria.where("_id").is(key.id())),
                new Update()
                        .setOnInsert("_id", key.id())
                        .setOnInsert("bucketStart", key.start())
                        .setOnInsert("partition", key.partition())
                        .setOnInsert(
                                "expiresAt",
                                key.start().plus(retention)
                        )
                        .inc("eventCount", events.size())
                        .push("events").each(embedded),
                COLLECTION
        );
    }

    private static BucketKey bucket(RawReadingEvent event) {
        Instant start = event.occurredAt().truncatedTo(ChronoUnit.MINUTES);
        String partition = event.sessionRef().substring(0, 2);
        return new BucketKey(
                start.getEpochSecond() / 60 + ":" + partition,
                start,
                partition
        );
    }

    private static EmbeddedEvent embedded(RawReadingEvent event) {
        return new EmbeddedEvent(
                event.eventId(),
                event.kind().name(),
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

    private record BucketKey(
            String id,
            Instant start,
            String partition
    ) {
    }

    public record EmbeddedEvent(
            String eventId,
            String kind,
            String sessionRef,
            String actorRef,
            String storyId,
            String chapterId,
            long sequence,
            Instant occurredAt,
            double position,
            Integer activeSeconds,
            Instant receivedAt
    ) {
    }
}
