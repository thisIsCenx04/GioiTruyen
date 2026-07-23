package com.storyplatform.teams.infrastructure.persistence;

import com.storyplatform.teams.application.port.TeamRepository;
import com.storyplatform.teams.domain.Team;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MongoTeamRepository implements TeamRepository {

    private final MongoTemplate mongo;

    public MongoTeamRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public boolean insertIfSlugAvailable(Team team) {
        Update insert = new Update()
                .setOnInsert("_id", team.id())
                .setOnInsert("slug", team.slug())
                .setOnInsert("name", team.name())
                .setOnInsert("description", team.description())
                .setOnInsert("ownerUserId", team.ownerUserId())
                .setOnInsert("state", team.state())
                .setOnInsert("createdAt", team.createdAt())
                .setOnInsert("updatedAt", team.updatedAt())
                .setOnInsert("version", team.version());
        return mongo.upsert(
                Query.query(Criteria.where("slug").is(team.slug())),
                insert,
                MongoTeamDocument.class
        ).getUpsertedId() != null;
    }

    @Override
    public Optional<Team> findById(String teamId) {
        return Optional.ofNullable(mongo.findById(
                teamId,
                MongoTeamDocument.class
        )).map(MongoTeamDocument::toDomain);
    }

    @Override
    public List<Team> listActive(int limit) {
        Query query = Query.query(Criteria.where("state").is(
                        Team.State.ACTIVE
                ))
                .with(Sort.by(
                        Sort.Order.desc("updatedAt"),
                        Sort.Order.desc("_id")
                ))
                .limit(limit);
        return mongo.find(query, MongoTeamDocument.class).stream()
                .map(MongoTeamDocument::toDomain)
                .toList();
    }

    @Override
    public UpdateResult updateOwned(
            String teamId,
            String ownerUserId,
            long version,
            String name,
            String description,
            Instant now
    ) {
        Query expected = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(teamId),
                Criteria.where("ownerUserId").is(ownerUserId),
                Criteria.where("state").is(Team.State.ACTIVE),
                Criteria.where("version").is(version)
        ));
        if (mongo.updateFirst(
                expected,
                new Update()
                        .set("name", name)
                        .set("description", description)
                        .set("updatedAt", now)
                        .inc("version", 1),
                MongoTeamDocument.class
        ).getModifiedCount() == 1) {
            return UpdateResult.UPDATED;
        }
        boolean owned = mongo.exists(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(teamId),
                        Criteria.where("ownerUserId").is(ownerUserId),
                        Criteria.where("state").is(Team.State.ACTIVE)
                )),
                MongoTeamDocument.class
        );
        return owned
                ? UpdateResult.VERSION_CONFLICT
                : UpdateResult.NOT_OWNED_OR_NOT_FOUND;
    }
}
