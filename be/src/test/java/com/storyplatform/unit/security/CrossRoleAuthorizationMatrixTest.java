package com.storyplatform.unit.security;

import com.storyplatform.shared.security.PrivilegedCapability;
import com.storyplatform.shared.security.RoleCapabilityPolicy;
import com.storyplatform.teams.application.TeamAuthorizationUseCase;
import com.storyplatform.teams.application.port.TeamMembershipCache;
import com.storyplatform.teams.application.port.TeamMembershipRepository;
import com.storyplatform.teams.domain.TeamMembership;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrossRoleAuthorizationMatrixTest {

    private static final Instant JOINED_AT = Instant.parse(
            "2026-07-25T00:00:00Z"
    );
    private static final String TEAM_A = "team-a";
    private static final String TEAM_B = "team-b";
    private static final Set<PrivilegedCapability> MODERATOR_CAPABILITIES =
            Set.of(
                    PrivilegedCapability.MODERATION_QUEUE_READ,
                    PrivilegedCapability.MODERATION_DECIDE,
                    PrivilegedCapability.CONTENT_SUSPEND
            );
    private static final Set<String> TEAM_PERMISSIONS = Set.of(
            TeamAuthorizationUseCase.MANAGE_TEAM,
            "story:create",
            "story:edit",
            "story:submit",
            "story:publish",
            "analytics:read",
            "finance:request"
    );

    @Test
    void privilegedRoleCapabilityMatrixFailsClosed() {
        RoleCapabilityPolicy policy = new RoleCapabilityPolicy();
        List<RoleCase> roles = List.of(
                new RoleCase("admin", Set.of("ADMIN"), RoleKind.ADMIN),
                new RoleCase(
                        "admin with untrusted role",
                        Set.of("ADMIN", "USER"),
                        RoleKind.ADMIN
                ),
                new RoleCase(
                        "moderator",
                        Set.of("MODERATOR"),
                        RoleKind.MODERATOR
                ),
                new RoleCase(
                        "moderator with user role",
                        Set.of("MODERATOR", "USER"),
                        RoleKind.MODERATOR
                ),
                new RoleCase("reader", Set.of("USER"), RoleKind.UNTRUSTED),
                new RoleCase(
                        "invented finance role",
                        Set.of("FINANCE_REVIEWER"),
                        RoleKind.UNTRUSTED
                ),
                new RoleCase(
                        "lowercase admin",
                        Set.of("admin"),
                        RoleKind.UNTRUSTED
                ),
                new RoleCase("no role", Set.of(), RoleKind.UNTRUSTED)
        );

        SoftAssertions softly = new SoftAssertions();
        for (RoleCase role : roles) {
            for (PrivilegedCapability capability
                    : PrivilegedCapability.values()) {
                boolean expected = switch (role.kind()) {
                    case ADMIN -> true;
                    case MODERATOR ->
                            MODERATOR_CAPABILITIES.contains(capability);
                    case UNTRUSTED -> false;
                };
                softly.assertThat(policy.allows(
                                role.roles(),
                                capability
                        ))
                        .as("%s -> %s", role.label(), capability)
                        .isEqualTo(expected);
            }
        }
        for (PrivilegedCapability capability
                : PrivilegedCapability.values()) {
            softly.assertThat(policy.allows(null, capability))
                    .as("null roles -> %s", capability)
                    .isFalse();
        }
        softly.assertAll();
    }

    @Test
    void teamResourceMatrixPreventsCrossUserAndCrossTeamAccess() {
        TeamMembership owner = TeamMembership.owner(
                TEAM_A,
                "owner-a",
                JOINED_AT
        );
        TeamMembership editor = member(
                "editor-a",
                Set.of("story:edit", "analytics:read"),
                TeamMembership.State.ACTIVE
        );
        TeamMembership finance = member(
                "finance-a",
                Set.of("finance:request"),
                TeamMembership.State.ACTIVE
        );
        TeamMembership revoked = member(
                "revoked-a",
                Set.of("story:publish", "finance:request"),
                TeamMembership.State.REVOKED
        );
        Map<ResourceKey, TeamMembership> authoritative = Map.of(
                new ResourceKey(TEAM_A, "owner-a"), owner,
                new ResourceKey(TEAM_A, "editor-a"), editor,
                new ResourceKey(TEAM_A, "finance-a"), finance,
                new ResourceKey(TEAM_A, "revoked-a"), revoked
        );
        TeamMembershipRepository repository =
                mock(TeamMembershipRepository.class);
        TeamMembershipCache cache = mock(TeamMembershipCache.class);
        when(repository.find(anyString(), anyString())).thenAnswer(
                invocation -> Optional.ofNullable(authoritative.get(
                        new ResourceKey(
                                invocation.getArgument(0),
                                invocation.getArgument(1)
                        )
                ))
        );
        when(cache.get(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation ->
                        TeamMembershipCache.Snapshot.from(
                                invocation.getArgument(0)
                        ));
        TeamAuthorizationUseCase policy =
                new TeamAuthorizationUseCase(repository, cache);
        List<String> actors = List.of(
                "owner-a",
                "editor-a",
                "finance-a",
                "revoked-a",
                "admin-without-membership",
                "owner-b"
        );
        Set<String> testedPermissions = new java.util.HashSet<>(
                TEAM_PERMISSIONS
        );
        testedPermissions.add("admin:all");

        SoftAssertions softly = new SoftAssertions();
        for (String actor : actors) {
            for (String team : List.of(TEAM_A, TEAM_B)) {
                for (String permission : testedPermissions) {
                    softly.assertThat(policy.allows(
                                    actor,
                                    team,
                                    permission
                            ))
                            .as("%s -> %s / %s", actor, team, permission)
                            .isEqualTo(expectedTeamAccess(
                                    actor,
                                    team,
                                    permission
                            ));
                }
            }
        }
        softly.assertAll();
    }

    private static boolean expectedTeamAccess(
            String actor,
            String team,
            String permission
    ) {
        if (!TEAM_A.equals(team) || !TEAM_PERMISSIONS.contains(permission)) {
            return false;
        }
        return switch (actor) {
            case "owner-a" -> true;
            case "editor-a" -> Set.of(
                    "story:edit",
                    "analytics:read"
            ).contains(permission);
            case "finance-a" -> "finance:request".equals(permission);
            default -> false;
        };
    }

    private static TeamMembership member(
            String userId,
            Set<String> permissions,
            TeamMembership.State state
    ) {
        return new TeamMembership(
                TEAM_A + ":" + userId,
                TEAM_A,
                userId,
                TeamMembership.Role.MEMBER,
                permissions,
                state,
                JOINED_AT,
                1
        );
    }

    private enum RoleKind {
        ADMIN,
        MODERATOR,
        UNTRUSTED
    }

    private record RoleCase(
            String label,
            Set<String> roles,
            RoleKind kind
    ) {
    }

    private record ResourceKey(String teamId, String userId) {
    }
}
