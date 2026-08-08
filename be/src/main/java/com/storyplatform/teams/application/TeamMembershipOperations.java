package com.storyplatform.teams.application;

import java.util.List;
import java.util.Set;

public interface TeamMembershipOperations {

    List<MembershipView> list(String actorId, String teamId);

    MembershipView invite(
            String actorId,
            String teamId,
            String targetUserId,
            Set<String> permissions,
            String idempotencyKey
    );

    MembershipView accept(String actorId, String rawToken);

    MembershipView updatePermissions(
            String actorId,
            String teamId,
            String targetUserId,
            long version,
            Set<String> permissions
    );

    void remove(
            String actorId,
            String teamId,
            String targetUserId,
            long version
    );

    record MembershipView(
            String teamId,
            String userId,
            String role,
            Set<String> permissions,
            String state,
            java.time.Instant joinedAt,
            long version
    ) {
        public MembershipView {
            permissions = Set.copyOf(permissions);
        }
    }
}
