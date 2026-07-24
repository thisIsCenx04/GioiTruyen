package com.storyplatform.teams.infrastructure;

import com.storyplatform.teams.infrastructure.persistence
        .MongoTeamFollowCounterDocument;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Clock;
import java.util.Objects;

public class TeamFollowCounterStore {

    private final MongoTemplate mongo;
    private final Clock clock;

    public TeamFollowCounterStore(MongoTemplate mongo, Clock clock) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void applyDelta(String teamId, int delta) {
        if (delta == 1) {
            Query query = Query.query(Criteria.where("_id").is(teamId));
            Update update = new Update()
                    .inc("followerCount", 1)
                    .set("updatedAt", clock.instant());
            mongo.upsert(
                    query,
                    update,
                    MongoTeamFollowCounterDocument.class
            );
            return;
        }
        if (delta != -1) {
            throw new IllegalArgumentException(
                    "follow counter delta must be 1 or -1"
            );
        }
        Query query = Query.query(Criteria.where("_id").is(teamId)
                .and("followerCount").gt(0));
        Update update = new Update()
                .inc("followerCount", -1)
                .set("updatedAt", clock.instant());
        mongo.updateFirst(
                query,
                update,
                MongoTeamFollowCounterDocument.class
        );
    }

    public void reconcile(String teamId, long authoritativeCount) {
        if (authoritativeCount < 0) {
            throw new IllegalArgumentException(
                    "authoritative follow count must not be negative"
            );
        }
        Query query = Query.query(Criteria.where("_id").is(teamId));
        Update update = new Update()
                .set("followerCount", authoritativeCount)
                .set("updatedAt", clock.instant());
        mongo.upsert(
                query,
                update,
                MongoTeamFollowCounterDocument.class
        );
    }
}
