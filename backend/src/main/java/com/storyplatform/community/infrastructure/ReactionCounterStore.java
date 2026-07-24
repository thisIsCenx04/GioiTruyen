package com.storyplatform.community.infrastructure;

import com.storyplatform.community.domain.Reaction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class ReactionCounterStore {

    public static final String COLLECTION = "reaction_counters";
    private final MongoTemplate mongo;
    private final Clock clock;

    public ReactionCounterStore(MongoTemplate mongo, Clock clock) {
        this.mongo = Objects.requireNonNull(mongo);
        this.clock = Objects.requireNonNull(clock);
    }

    public void applyDelta(
            Reaction.TargetType targetType,
            String targetId,
            int delta
    ) {
        String id = targetType + ":" + targetId;
        if (delta == 1) {
            mongo.upsert(
                    Query.query(Criteria.where("_id").is(id)),
                    new Update()
                            .setOnInsert("targetType", targetType.name())
                            .setOnInsert("targetId", targetId)
                            .set("updatedAt", clock.instant())
                            .inc("count", 1),
                    CounterDocument.class,
                    COLLECTION
            );
            return;
        }
        if (delta != -1) {
            throw new IllegalArgumentException(
                    "reaction counter delta must be 1 or -1"
            );
        }
        mongo.updateFirst(
                Query.query(Criteria.where("_id").is(id)
                        .and("count").gt(0)),
                new Update()
                        .set("updatedAt", clock.instant())
                        .inc("count", -1),
                CounterDocument.class,
                COLLECTION
        );
    }

    public record CounterDocument(
            String id,
            String targetType,
            String targetId,
            long count,
            Instant updatedAt
    ) {
    }
}
