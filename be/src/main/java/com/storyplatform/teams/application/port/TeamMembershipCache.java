package com.storyplatform.teams.application.port;

import com.storyplatform.teams.domain.TeamMembership;

import java.util.Set;

public interface TeamMembershipCache {

    Snapshot get(TeamMembership authoritativeMembership);

    record Snapshot(
            String role,
            Set<String> permissions,
            boolean active,
            long version
    ) {
        public Snapshot {
            permissions = Set.copyOf(permissions);
        }

        public static Snapshot from(TeamMembership membership) {
            return new Snapshot(
                    membership.role().name(),
                    membership.permissions(),
                    membership.state() == TeamMembership.State.ACTIVE,
                    membership.version()
            );
        }
    }
}
