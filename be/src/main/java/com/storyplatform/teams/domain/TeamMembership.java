package com.storyplatform.teams.domain;

import java.time.Instant;
import java.util.Set;

public record TeamMembership(
        String id,
        String teamId,
        String userId,
        Role role,
        Set<String> permissions,
        State state,
        Instant joinedAt,
        long version
) {
    public TeamMembership {
        permissions = Set.copyOf(permissions);
    }

    public static TeamMembership owner(
            String teamId,
            String userId,
            Instant now
    ) {
        return new TeamMembership(
                teamId + ":" + userId,
                teamId,
                userId,
                Role.OWNER,
                Set.of("team:manage"),
                State.ACTIVE,
                now,
                0
        );
    }

    public static TeamMembership invited(
            String teamId,
            String userId,
            Set<String> permissions,
            Instant now
    ) {
        return new TeamMembership(
                teamId + ":" + userId,
                teamId,
                userId,
                Role.MEMBER,
                permissions,
                State.INVITED,
                now,
                0
        );
    }

    public TeamMembership activate(Instant now) {
        return new TeamMembership(
                id,
                teamId,
                userId,
                role,
                permissions,
                State.ACTIVE,
                now,
                version + 1
        );
    }

    public enum Role {
        OWNER,
        MEMBER
    }

    public enum State {
        ACTIVE,
        INVITED,
        REVOKED
    }
}
