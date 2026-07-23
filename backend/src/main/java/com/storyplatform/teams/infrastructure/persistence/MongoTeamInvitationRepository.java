package com.storyplatform.teams.infrastructure.persistence;

import com.storyplatform.teams.application.port.TeamInvitationRepository;
import com.storyplatform.teams.domain.TeamInvitation;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MongoTeamInvitationRepository
        implements TeamInvitationRepository {

    private final MongoTemplate mongo;

    public MongoTeamInvitationRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public void insert(TeamInvitation invitation) {
        mongo.insert(MongoTeamInvitationDocument.from(invitation));
    }

    @Override
    public Optional<TeamInvitation> findByIdempotencyKey(
            String teamId,
            String key
    ) {
        Query query = Query.query(Criteria.where("teamId").is(teamId)
                .and("idempotencyKey").is(key));
        return find(query);
    }

    @Override
    public Optional<TeamInvitation> findByTokenHash(String tokenHash) {
        return find(Query.query(Criteria.where("tokenHash").is(tokenHash)));
    }

    @Override
    public boolean accept(
            String invitationId,
            long version,
            Instant acceptedAt
    ) {
        Query query = Query.query(Criteria.where("_id").is(invitationId)
                .and("state").is(TeamInvitation.State.PENDING)
                .and("version").is(version)
                .and("expiresAt").gt(acceptedAt));
        Update update = new Update()
                .set("state", TeamInvitation.State.ACCEPTED)
                .set("acceptedAt", acceptedAt)
                .inc("version", 1);
        return mongo.updateFirst(
                query,
                update,
                MongoTeamInvitationDocument.class
        ).getModifiedCount() == 1;
    }

    private Optional<TeamInvitation> find(Query query) {
        return Optional.ofNullable(mongo.findOne(
                query,
                MongoTeamInvitationDocument.class
        )).map(MongoTeamInvitationDocument::toDomain);
    }
}
