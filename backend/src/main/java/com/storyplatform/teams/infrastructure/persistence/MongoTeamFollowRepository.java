package com.storyplatform.teams.infrastructure.persistence;

import com.storyplatform.teams.application.port.TeamFollowRepository;
import com.storyplatform.teams.domain.TeamFollow;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.Objects;

@Repository
public class MongoTeamFollowRepository implements TeamFollowRepository {

    private final MongoTemplate mongo;

    public MongoTeamFollowRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public boolean insertIfAbsent(TeamFollow follow) {
        Query query = Query.query(Criteria.where("_id").is(follow.id()));
        MongoTeamFollowDocument document =
                MongoTeamFollowDocument.from(follow);
        Update update = new Update()
                .setOnInsert("teamId", document.teamId())
                .setOnInsert("userId", document.userId())
                .setOnInsert("createdAt", document.createdAt());
        return mongo.upsert(
                query,
                update,
                MongoTeamFollowDocument.class
        ).getUpsertedId() != null;
    }

    @Override
    public boolean deleteIfPresent(String teamId, String userId) {
        Query query = Query.query(Criteria.where("teamId").is(teamId)
                .and("userId").is(userId));
        return mongo.remove(
                query,
                MongoTeamFollowDocument.class
        ).getDeletedCount() == 1;
    }

    @Override
    public boolean exists(String teamId, String userId) {
        Query query = Query.query(Criteria.where("teamId").is(teamId)
                .and("userId").is(userId));
        return mongo.exists(query, MongoTeamFollowDocument.class);
    }

    @Override
    public long count(String teamId) {
        return mongo.count(
                Query.query(Criteria.where("teamId").is(teamId)),
                MongoTeamFollowDocument.class
        );
    }
}
