package com.storyplatform.discovery.infrastructure.persistence;

import com.storyplatform.discovery.application.port.RankingRepository;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MongoRankingRepository implements RankingRepository {

    private static final String AGGREGATES = "reading_view_aggregates";
    private final MongoTemplate mongo;

    public MongoRankingRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public List<RankedSubject> stories(
            Instant from,
            Instant to,
            Metric metric,
            int limit
    ) {
        List<Document> pipeline = base(from, to);
        pipeline.add(new Document("$lookup", new Document()
                .append("from", "stories")
                .append("localField", "_id")
                .append("foreignField", "_id")
                .append("as", "subject")));
        pipeline.add(new Document("$unwind", "$subject"));
        pipeline.add(new Document(
                "$match",
                new Document("subject.workflowStatus", "PUBLISHED")
        ));
        pipeline.add(new Document("$set", new Document(
                "score", score(metric)
        )));
        pipeline.addAll(sort(limit));
        pipeline.add(new Document("$project", new Document()
                .append("_id", 1)
                .append("name", "$subject.title")
                .append("teamId", "$subject.teamId")
                .append("validViews", 1)
                .append("completedViews", 1)
                .append("invalidViews", 1)
                .append("score", 1)));
        return execute(pipeline);
    }

    @Override
    public List<RankedSubject> teams(
            Instant from,
            Instant to,
            Metric metric,
            int limit
    ) {
        List<Document> pipeline = base(from, to);
        pipeline.add(new Document("$lookup", new Document()
                .append("from", "stories")
                .append("localField", "_id")
                .append("foreignField", "_id")
                .append("as", "story")));
        pipeline.add(new Document("$unwind", "$story"));
        pipeline.add(new Document(
                "$match",
                new Document("story.workflowStatus", "PUBLISHED")
        ));
        pipeline.add(new Document("$group", new Document()
                .append("_id", "$story.teamId")
                .append("validViews", new Document("$sum", "$validViews"))
                .append(
                        "completedViews",
                        new Document("$sum", "$completedViews")
                )
                .append(
                        "invalidViews",
                        new Document("$sum", "$invalidViews")
                )));
        pipeline.add(new Document("$lookup", new Document()
                .append("from", "teams")
                .append("localField", "_id")
                .append("foreignField", "_id")
                .append("as", "subject")));
        pipeline.add(new Document("$unwind", "$subject"));
        pipeline.add(new Document(
                "$match",
                new Document("subject.state", "ACTIVE")
        ));
        pipeline.add(new Document("$set", new Document(
                "score", score(metric)
        )));
        pipeline.addAll(sort(limit));
        pipeline.add(new Document("$project", new Document()
                .append("_id", 1)
                .append("name", "$subject.name")
                .append("teamId", "$_id")
                .append("validViews", 1)
                .append("completedViews", 1)
                .append("invalidViews", 1)
                .append("score", 1)));
        return execute(pipeline);
    }

    private List<RankedSubject> execute(List<Document> pipeline) {
        List<RankedSubject> result = new ArrayList<>();
        mongo.getCollection(AGGREGATES)
                .aggregate(pipeline)
                .allowDiskUse(true)
                .forEach(document -> result.add(new RankedSubject(
                        document.getString("_id"),
                        document.getString("name"),
                        document.getString("teamId"),
                        number(document, "validViews").longValue(),
                        number(document, "completedViews").longValue(),
                        number(document, "invalidViews").longValue(),
                        number(document, "score").doubleValue()
                )));
        return List.copyOf(result);
    }

    private static List<Document> base(Instant from, Instant to) {
        return new ArrayList<>(List.of(
                new Document("$match", new Document()
                        .append("period", "DAY")
                        .append(
                                "bucketStart",
                                new Document("$gte", from)
                                        .append("$lt", to)
                        )),
                new Document("$group", new Document()
                        .append("_id", "$storyId")
                        .append(
                                "validViews",
                                new Document("$sum", "$validViews")
                        )
                        .append(
                                "completedViews",
                                new Document("$sum", "$completedViews")
                        )
                        .append(
                                "invalidViews",
                                new Document("$sum", "$invalidViews")
                        ))
        ));
    }

    private static List<Document> sort(int limit) {
        return List.of(
                new Document("$sort", new Document()
                        .append("score", -1)
                        .append("validViews", -1)
                        .append("_id", 1)),
                new Document("$limit", limit)
        );
    }

    private static Object score(Metric metric) {
        if (metric == Metric.VALID_VIEWS) {
            return "$validViews";
        }
        String denominator = metric == Metric.COMPLETION_RATE
                ? "$completedViews"
                : null;
        Object divisor = denominator == null
                ? new Document("$add", List.of(
                        "$validViews", "$invalidViews"
                ))
                : denominator;
        return new Document("$cond", List.of(
                new Document("$gt", List.of(divisor, 0)),
                new Document("$divide", List.of("$validViews", divisor)),
                0
        ));
    }

    private static Number number(Document value, String field) {
        Object number = value.get(field);
        return number instanceof Number result ? result : 0;
    }
}
