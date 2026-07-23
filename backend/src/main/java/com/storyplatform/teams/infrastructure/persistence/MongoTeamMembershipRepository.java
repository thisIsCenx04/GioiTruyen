package com.storyplatform.teams.infrastructure.persistence;

import com.storyplatform.teams.application.port.TeamMembershipRepository;
import com.storyplatform.teams.domain.TeamMembership;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.util.Objects;

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
}
