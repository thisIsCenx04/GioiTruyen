package com.storyplatform.community.infrastructure;

import com.storyplatform.community.domain.StoryRelation;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public class StoryRelationCounterStore {

    public static final String COLLECTION = "story_relation_counters";
    private final MongoTemplate mongo;
    private final Clock clock;

    public StoryRelationCounterStore(MongoTemplate mongo, Clock clock) {
        this.mongo = Objects.requireNonNull(mongo);
        this.clock = Objects.requireNonNull(clock);
    }

    public void applyDelta(
            String storyId,
            StoryRelation.Type type,
            int delta
    ) {
        String field = type == StoryRelation.Type.FAVORITE
                ? "favoriteCount"
                : "followerCount";
        if (delta == 1) {
            mongo.upsert(
                    Query.query(Criteria.where("_id").is(storyId)),
                    new Update()
                            .setOnInsert("storyId", storyId)
                            .set("updatedAt", clock.instant())
                            .inc(field, 1),
                    CounterDocument.class,
                    COLLECTION
            );
            return;
        }
        if (delta != -1) {
            throw new IllegalArgumentException(
                    "relation counter delta must be 1 or -1"
            );
        }
        mongo.updateFirst(
                Query.query(Criteria.where("_id").is(storyId)
                        .and(field).gt(0)),
                new Update()
                        .set("updatedAt", clock.instant())
                        .inc(field, -1),
                CounterDocument.class,
                COLLECTION
        );
    }

    public void reconcile(
            String storyId,
            long favorites,
            long followers
    ) {
        if (favorites < 0 || followers < 0) {
            throw new IllegalArgumentException(
                    "authoritative relation counts must not be negative"
            );
        }
        mongo.upsert(
                Query.query(Criteria.where("_id").is(storyId)),
                new Update()
                        .set("storyId", storyId)
                        .set("favoriteCount", favorites)
                        .set("followerCount", followers)
                        .set("updatedAt", clock.instant()),
                CounterDocument.class,
                COLLECTION
        );
    }

    public record CounterDocument(
            String id,
            String storyId,
            long favoriteCount,
            long followerCount,
            Instant updatedAt
    ) {
    }
}
