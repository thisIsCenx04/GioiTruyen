package com.storyplatform.teams.infrastructure.persistence;

import com.storyplatform.teams.application.port.TeamMembershipRepository;
import com.storyplatform.teams.domain.TeamMembership;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Repository
public class MongoTeamMembershipRepository
        implements TeamMembershipRepository {

    private final MongoTemplate mongo;

    public MongoTeamMembershipRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public void insertOwner(TeamMembership membership) {
        mongo.insert(MongoTeamMembershipDocument.from(membership));
    }

    @Override
    public void insert(TeamMembership membership) {
        mongo.insert(MongoTeamMembershipDocument.from(membership));
    }

    @Override
    public Optional<TeamMembership> find(String teamId, String userId) {
        Query query = Query.query(Criteria.where("teamId").is(teamId)
                .and("userId").is(userId));
        return Optional.ofNullable(mongo.findOne(
                query,
                MongoTeamMembershipDocument.class
        )).map(MongoTeamMembershipDocument::toDomain);
    }

    @Override
    public List<TeamMembership> list(String teamId) {
        Query query = Query.query(Criteria.where("teamId").is(teamId));
        return mongo.find(query, MongoTeamMembershipDocument.class).stream()
                .map(MongoTeamMembershipDocument::toDomain)
                .toList();
    }

    @Override
    public boolean activate(
            String teamId,
            String userId,
            long version,
            Instant joinedAt
    ) {
        Query query = Query.query(Criteria.where("teamId").is(teamId)
                .and("userId").is(userId)
                .and("state").is(TeamMembership.State.INVITED)
                .and("version").is(version));
        Update update = new Update()
                .set("state", TeamMembership.State.ACTIVE)
                .set("joinedAt", joinedAt)
                .inc("version", 1);
        return mongo.updateFirst(
                query,
                update,
                MongoTeamMembershipDocument.class
        ).getModifiedCount() == 1;
    }

    @Override
    public PermissionUpdateResult updatePermissions(
            String teamId,
            String userId,
            long version,
            Set<String> permissions
    ) {
        Query query = Query.query(Criteria.where("teamId").is(teamId)
                .and("userId").is(userId)
                .and("role").is(TeamMembership.Role.MEMBER)
                .and("state").is(TeamMembership.State.ACTIVE)
                .and("version").is(version));
        Update update = new Update()
                .set("permissions", Set.copyOf(permissions))
                .inc("version", 1);
        if (mongo.updateFirst(
                query,
                update,
                MongoTeamMembershipDocument.class
        ).getModifiedCount() == 1) {
            return PermissionUpdateResult.UPDATED;
        }
        Query existence = Query.query(Criteria.where("teamId").is(teamId)
                .and("userId").is(userId)
                .and("role").is(TeamMembership.Role.MEMBER)
                .and("state").is(TeamMembership.State.ACTIVE));
        if (mongo.exists(
                existence,
                MongoTeamMembershipDocument.class
        )) {
            return PermissionUpdateResult.VERSION_CONFLICT;
        }
        return PermissionUpdateResult.NOT_FOUND;
    }

    @Override
    public RemovalResult revokeMember(
            String teamId,
            String userId,
            long version
    ) {
        Optional<TeamMembership> current = find(teamId, userId);
        if (current.isEmpty()) {
            return RemovalResult.NOT_FOUND_OR_CONFLICT;
        }
        if (current.get().role() == TeamMembership.Role.OWNER) {
            return RemovalResult.LAST_OWNER;
        }
        Query query = Query.query(Criteria.where("teamId").is(teamId)
                .and("userId").is(userId)
                .and("version").is(version)
                .and("state").ne(TeamMembership.State.REVOKED));
        Update update = new Update()
                .set("state", TeamMembership.State.REVOKED)
                .inc("version", 1);
        if (mongo.updateFirst(
                query,
                update,
                MongoTeamMembershipDocument.class
        ).getModifiedCount() == 1) {
            return RemovalResult.REMOVED;
        }
        return RemovalResult.NOT_FOUND_OR_CONFLICT;
    }
}
