package com.storyplatform.teams.application.port;

import com.storyplatform.teams.domain.TeamMembership;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TeamMembershipRepository {

    void insertOwner(TeamMembership membership);

    void insert(TeamMembership membership);

    Optional<TeamMembership> find(String teamId, String userId);

    List<TeamMembership> list(String teamId);

    boolean activate(
            String teamId,
            String userId,
            long version,
            Instant joinedAt
    );

    PermissionUpdateResult updatePermissions(
            String teamId,
            String userId,
            long version,
            Set<String> permissions
    );

    RemovalResult revokeMember(
            String teamId,
            String userId,
            long version
    );

    enum RemovalResult {
        REMOVED,
        LAST_OWNER,
        NOT_FOUND_OR_CONFLICT
    }

    enum PermissionUpdateResult {
        UPDATED,
        VERSION_CONFLICT,
        NOT_FOUND
    }
}
