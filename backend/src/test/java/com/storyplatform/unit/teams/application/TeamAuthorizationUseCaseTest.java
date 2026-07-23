package com.storyplatform.unit.teams.application;

import com.storyplatform.teams.application.TeamAuthorizationUseCase;
import com.storyplatform.teams.application.port.TeamMembershipCache;
import com.storyplatform.teams.application.port.TeamMembershipRepository;
import com.storyplatform.teams.domain.TeamMembership;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamAuthorizationUseCaseTest {

    private static final Instant NOW = Instant.parse(
            "2026-07-24T00:00:00Z"
    );

    private final TeamMembershipRepository memberships =
            mock(TeamMembershipRepository.class);
    private final TeamMembershipCache cache =
            mock(TeamMembershipCache.class);
    private TeamAuthorizationUseCase policy;

    @BeforeEach
    void setUp() {
        policy = new TeamAuthorizationUseCase(memberships, cache);
    }

    @Test
    void activeMemberUsesVersionValidatedPermissionSnapshot() {
        TeamMembership member = member(
                TeamMembership.State.ACTIVE,
                4
        );
        when(memberships.find("team-1", "user-1"))
                .thenReturn(Optional.of(member));
        when(cache.get(member)).thenReturn(new TeamMembershipCache.Snapshot(
                "MEMBER",
                Set.of("story:edit"),
                true,
                4
        ));

        assertThat(policy.allows(
                "user-1",
                "team-1",
                "story:edit"
        )).isTrue();
        assertThat(policy.allows(
                "user-1",
                "team-1",
                "story:publish"
        )).isFalse();
        verify(memberships, times(2)).find("team-1", "user-1");
    }

    @Test
    void staleCacheRevokedMembershipAndUnknownPermissionFailClosed() {
        TeamMembership active = member(
                TeamMembership.State.ACTIVE,
                5
        );
        when(memberships.find("team-1", "user-1"))
                .thenReturn(Optional.of(active));
        when(cache.get(active)).thenReturn(new TeamMembershipCache.Snapshot(
                "MEMBER",
                Set.of("story:publish"),
                true,
                4
        ));
        assertThat(policy.allows(
                "user-1",
                "team-1",
                "story:publish"
        )).isFalse();

        when(memberships.find("team-1", "user-1"))
                .thenReturn(Optional.of(member(
                        TeamMembership.State.REVOKED,
                        6
                )));
        assertThat(policy.allows(
                "user-1",
                "team-1",
                "story:publish"
        )).isFalse();
        assertThat(policy.allows(
                "user-1",
                "team-1",
                "admin:all"
        )).isFalse();
    }

    @Test
    void activeOwnerInheritsTeamAndMemberActionsOnly() {
        TeamMembership owner = TeamMembership.owner(
                "team-1",
                "owner-1",
                NOW
        );
        when(memberships.find("team-1", "owner-1"))
                .thenReturn(Optional.of(owner));
        when(cache.get(owner)).thenReturn(
                TeamMembershipCache.Snapshot.from(owner)
        );

        assertThat(policy.allows(
                "owner-1",
                "team-1",
                "team:manage"
        )).isTrue();
        assertThat(policy.allows(
                "owner-1",
                "team-1",
                "finance:request"
        )).isTrue();
    }

    private static TeamMembership member(
            TeamMembership.State state,
            long version
    ) {
        return new TeamMembership(
                "team-1:user-1",
                "team-1",
                "user-1",
                TeamMembership.Role.MEMBER,
                Set.of("story:edit"),
                state,
                NOW,
                version
        );
    }
}
