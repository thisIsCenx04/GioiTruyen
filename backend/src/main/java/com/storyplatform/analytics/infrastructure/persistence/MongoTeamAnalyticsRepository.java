package com.storyplatform.analytics.infrastructure.persistence;

import com.storyplatform.analytics.application.port.TeamAnalyticsRepository;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MongoTeamAnalyticsRepository
        implements TeamAnalyticsRepository {

    private final MongoTemplate mongo;

    public MongoTeamAnalyticsRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public List<DailyAggregate> daily(
            String teamId,
            Instant from,
            Instant to
    ) {
        List<DailyAggregate> result = new ArrayList<>();
        collection().aggregate(pipeline(teamId, from, to))
                .allowDiskUse(true)
                .forEach(row -> result.add(map(row)));
        return List.copyOf(result);
    }

    MongoCollection<Document> collection() {
        return mongo.getCollection(MongoViewAggregateRepository.COLLECTION);
    }

    static List<Document> pipeline(
            String teamId,
            Instant from,
            Instant to
    ) {
        return List.of(
                new Document("$match", new Document()
                        .append("period", "DAY")
                        .append("bucketStart", new Document("$gte", from)
                                .append("$lt", to))),
                new Document("$lookup", new Document()
                        .append("from", "stories")
                        .append("localField", "storyId")
                        .append("foreignField", "_id")
                        .append("as", "story")),
                new Document("$unwind", "$story"),
                new Document("$match", new Document("story.teamId", teamId)),
                new Document("$group", new Document()
                        .append("_id", "$bucketStart")
                        .append("rawEvents", new Document(
                                "$sum", "$rawEvents"))
                        .append("completedViews", new Document(
                                "$sum", "$completedViews"))
                        .append("validViews", new Document(
                                "$sum", "$validViews"))
                        .append("invalidViews", new Document(
                                "$sum", "$invalidViews"))
                        .append("reasonMaps", new Document(
                                "$push", "$reasonCounts"))),
                new Document("$sort", new Document("_id", 1))
        );
    }

    private static DailyAggregate map(Document row) {
        Map<String, Long> reasons = new LinkedHashMap<>();
        List<?> maps = row.getList("reasonMaps", Object.class, List.of());
        for (Object value : maps) {
            if (value instanceof Document document) {
                document.forEach((code, count) -> {
                    if (count instanceof Number number) {
                        reasons.merge(
                                code,
                                number.longValue(),
                                Long::sum
                        );
                    }
                });
            }
        }
        return new DailyAggregate(
                instant(row.get("_id")),
                number(row, "rawEvents"),
                number(row, "completedViews"),
                number(row, "validViews"),
                number(row, "invalidViews"),
                reasons
        );
    }

    private static long number(Document row, String field) {
        Object value = row.get(field);
        return value instanceof Number number ? number.longValue() : 0;
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Date date) {
            return date.toInstant();
        }
        throw new IllegalStateException("Aggregate bucket start is missing.");
    }
}
