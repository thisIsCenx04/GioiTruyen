package com.storyplatform.analytics.infrastructure.persistence;

import com.mongodb.client.MongoCollection;
import com.storyplatform.analytics.application.contract
        .RewardViewAggregateDirectory;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public final class MongoRewardViewAggregateDirectory
        implements RewardViewAggregateDirectory {

    private final MongoTemplate mongo;

    public MongoRewardViewAggregateDirectory(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public String aggregateVersion() {
        return MongoViewAggregateRepository.AGGREGATE_VERSION;
    }

    @Override
    public List<TeamValidViews> validViewsByTeam(
            Instant from,
            Instant to
    ) {
        List<TeamValidViews> result = new ArrayList<>();
        collection().aggregate(pipeline(from, to))
                .allowDiskUse(true)
                .forEach(row -> result.add(new TeamValidViews(
                        row.getString("_id"),
                        number(row, "validViews")
                )));
        return List.copyOf(result);
    }

    private MongoCollection<Document> collection() {
        return mongo.getCollection(MongoViewAggregateRepository.COLLECTION);
    }

    static List<Document> pipeline(Instant from, Instant to) {
        return List.of(
                new Document("$match", new Document()
                        .append("period", "DAY")
                        .append("aggregateVersion",
                                MongoViewAggregateRepository
                                        .AGGREGATE_VERSION)
                        .append("bucketStart", new Document(
                                "$gte", Date.from(from)
                        ).append("$lt", Date.from(to)))),
                new Document("$lookup", new Document()
                        .append("from", "stories")
                        .append("localField", "storyId")
                        .append("foreignField", "_id")
                        .append("as", "story")),
                new Document("$unwind", "$story"),
                new Document("$group", new Document()
                        .append("_id", "$story.teamId")
                        .append("validViews", new Document(
                                "$sum", "$validViews"
                        ))),
                new Document("$match", new Document(
                        "validViews", new Document("$gt", 0)
                )),
                new Document("$sort", new Document("_id", 1))
        );
    }

    private static long number(Document row, String field) {
        Object value = row.get(field);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException(
                "Reward aggregate is missing " + field + "."
        );
    }
}
