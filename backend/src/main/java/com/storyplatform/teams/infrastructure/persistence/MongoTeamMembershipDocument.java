package com.storyplatform.teams.infrastructure.persistence;

import com.storyplatform.teams.domain.TeamMembership;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Set;

@Document(collection = MongoTeamMembershipDocument.COLLECTION)
public record MongoTeamMembershipDocument(
        @Id String id,
        String teamId,
        String userId,
        TeamMembership.Role role,
        Set<String> permissions,
        TeamMembership.State state,
        Instant joinedAt,
        long version
) {
    public static final String COLLECTION = "team_memberships";

    static MongoTeamMembershipDocument from(TeamMembership membership) {
        return new MongoTeamMembershipDocument(
                membership.id(),
                membership.teamId(),
                membership.userId(),
                membership.role(),
                membership.permissions(),
                membership.state(),
                membership.joinedAt(),
                membership.version()
        );
    }
}
