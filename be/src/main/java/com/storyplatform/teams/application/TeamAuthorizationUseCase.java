package com.storyplatform.teams.application;

import com.storyplatform.teams.application.port.TeamMembershipCache;
import com.storyplatform.teams.application.port.TeamMembershipRepository;
import com.storyplatform.teams.domain.TeamMembership;
import com.storyplatform.teams.domain.TeamPermissions;

import java.util.Objects;
import java.util.Optional;

public final class TeamAuthorizationUseCase
        implements TeamAuthorizationPolicy {

    public static final String MANAGE_TEAM = "team:manage";

    private final TeamMembershipRepository memberships;
    private final TeamMembershipCache cache;

    public TeamAuthorizationUseCase(
            TeamMembershipRepository memberships,
            TeamMembershipCache cache
    ) {
        this.memberships = Objects.requireNonNull(
                memberships,
                "memberships"
        );
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    @Override
    public boolean allows(
            String userId,
            String teamId,
            String permission
    ) {
        if (!isKnownPermission(permission)) {
            return false;
        }
        return memberships.find(teamId, userId)
                .filter(membership ->
                        membership.state() == TeamMembership.State.ACTIVE)
                .flatMap(this::validatedSnapshot)
                .map(snapshot -> allowed(snapshot, permission))
                .orElse(false);
    }

    private Optional<TeamMembershipCache.Snapshot> validatedSnapshot(
            TeamMembership membership
    ) {
        TeamMembershipCache.Snapshot snapshot = cache.get(membership);
        if (!snapshot.active()
                || snapshot.version() != membership.version()
                || !snapshot.role().equals(membership.role().name())
                || !snapshot.permissions().equals(
                        membership.permissions()
                )) {
            return Optional.empty();
        }
        return Optional.of(snapshot);
    }

    private static boolean allowed(
            TeamMembershipCache.Snapshot snapshot,
            String permission
    ) {
        if ("OWNER".equals(snapshot.role())) {
            return MANAGE_TEAM.equals(permission)
                    || TeamPermissions.MEMBER_ASSIGNABLE.contains(permission);
        }
        return snapshot.permissions().contains(permission);
    }

    private static boolean isKnownPermission(String permission) {
        return MANAGE_TEAM.equals(permission)
                || TeamPermissions.MEMBER_ASSIGNABLE.contains(permission);
    }
}
