package com.storyplatform.unit.teams.application;

import com.storyplatform.identity.application.contract.IdentityUserDirectory;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import com.storyplatform.teams.application.TeamAccessDeniedException;
import com.storyplatform.teams.application.TeamConflictException;
import com.storyplatform.teams.application.TeamInvitationInvalidException;
import com.storyplatform.teams.application.TeamMembershipUseCase;
import com.storyplatform.teams.application.TeamNotFoundException;
import com.storyplatform.teams.application.port.TeamInvitationRepository;
import com.storyplatform.teams.application.port.TeamInvitationTokenCodec;
import com.storyplatform.teams.application.port.TeamMembershipRepository;
import com.storyplatform.teams.application.port.TeamRepository;
import com.storyplatform.teams.domain.Team;
import com.storyplatform.teams.domain.TeamInvitation;
import com.storyplatform.teams.domain.TeamMembership;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamMembershipUseCaseTest {

    private static final Instant NOW = Instant.parse(
            "2026-07-24T00:00:00Z"
    );
    private static final String INVITATION_ID =
            "73457d55-9602-4bcd-bbf0-e38b99c6c56e";
    private static final String KEY = "request-123";

    private final TeamRepository teams = mock(TeamRepository.class);
    private final TeamMembershipRepository memberships =
            mock(TeamMembershipRepository.class);
    private final TeamInvitationRepository invitations =
            mock(TeamInvitationRepository.class);
    private final IdentityUserDirectory identities =
            mock(IdentityUserDirectory.class);
    private final TeamInvitationTokenCodec tokens =
            mock(TeamInvitationTokenCodec.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private TeamMembershipUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TeamMembershipUseCase(
                teams,
                memberships,
                invitations,
                identities,
                tokens,
                outbox,
                Duration.ofDays(7),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(teams.findById("team-1")).thenReturn(Optional.of(team()));
        when(memberships.find("team-1", "owner-1"))
                .thenReturn(Optional.of(owner()));
        when(identities.findById("user-2"))
                .thenReturn(Optional.of(activeUser()));
    }

    @Test
    void ownerInvitesActiveUserAndQueuesSafeNotification() {
        when(tokens.issue(anyString(), any())).thenReturn(
                new TeamInvitationTokenCodec.IssuedToken(
                        INVITATION_ID,
                        "token-hash"
                )
        );

        var result = useCase.invite(
                "owner-1",
                "team-1",
                "user-2",
                Set.of("story:create"),
                KEY
        );

        assertThat(result.state()).isEqualTo("INVITED");
        assertThat(result.permissions()).containsExactly("story:create");
        verify(memberships).insert(any(TeamMembership.class));
        verify(invitations).insert(any(TeamInvitation.class));
        ArgumentCaptor<IntegrationEvent> event =
                ArgumentCaptor.forClass(IntegrationEvent.class);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().eventType())
                .isEqualTo(TeamMembershipUseCase.INVITED_EVENT);
        assertThat(event.getValue().payload().toString())
                .doesNotContain("token-hash");
    }

    @Test
    void sameIdempotencyKeyReplaysMembershipWithoutSideEffects() {
        when(invitations.findByIdempotencyKey("team-1", KEY))
                .thenReturn(Optional.of(invitation(
                        TeamInvitation.State.PENDING,
                        NOW.plus(Duration.ofDays(7))
                )));
        when(memberships.find("team-1", "user-2"))
                .thenReturn(Optional.of(invited()));

        var firstReplay = useCase.invite(
                "owner-1",
                "team-1",
                "user-2",
                Set.of("story:create"),
                KEY
        );

        assertThat(firstReplay.state()).isEqualTo("INVITED");
        verify(invitations, never()).insert(any());
        verify(outbox, never()).append(any());
    }

    @Test
    void reusedKeyForDifferentPayloadAndDuplicateMemberConflict() {
        when(invitations.findByIdempotencyKey("team-1", KEY))
                .thenReturn(Optional.of(invitation(
                        TeamInvitation.State.PENDING,
                        NOW.plus(Duration.ofDays(7))
                )));
        assertThatThrownBy(() -> useCase.invite(
                "owner-1",
                "team-1",
                "user-2",
                Set.of("story:edit"),
                KEY
        )).isInstanceOf(TeamConflictException.class)
                .hasMessageContaining("another request");

        when(invitations.findByIdempotencyKey("team-1", KEY))
                .thenReturn(Optional.empty());
        when(memberships.find("team-1", "user-2"))
                .thenReturn(Optional.of(invited()));
        assertThatThrownBy(() -> useCase.invite(
                "owner-1",
                "team-1",
                "user-2",
                Set.of("story:create"),
                KEY
        )).isInstanceOf(TeamConflictException.class)
                .hasMessageContaining("already");
    }

    @Test
    void invitationRequiresOwnerActiveTargetPermissionsAndValidKey() {
        when(memberships.find("team-1", "member-1"))
                .thenReturn(Optional.of(invited()));
        assertThatThrownBy(() -> useCase.invite(
                "member-1",
                "team-1",
                "user-2",
                Set.of("story:create"),
                KEY
        )).isInstanceOf(TeamAccessDeniedException.class);

        when(identities.findById("user-2")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.invite(
                "owner-1",
                "team-1",
                "user-2",
                Set.of("story:create"),
                KEY
        )).isInstanceOf(TeamConflictException.class);

        when(identities.findById("user-2"))
                .thenReturn(Optional.of(activeUser()));
        assertThatThrownBy(() -> useCase.invite(
                "owner-1",
                "team-1",
                "user-2",
                Set.of("admin:all"),
                KEY
        )).isInstanceOf(TeamConflictException.class);
        assertThatThrownBy(() -> useCase.invite(
                "owner-1",
                "team-1",
                "user-2",
                Set.of("story:create"),
                "short"
        )).isInstanceOf(TeamConflictException.class);
    }

    @Test
    void targetAcceptsInvitationOnceAndRetryIsIdempotent() {
        when(tokens.isWellFormed("raw-token")).thenReturn(true);
        when(tokens.hash("raw-token")).thenReturn("token-hash");
        when(invitations.findByTokenHash("token-hash"))
                .thenReturn(Optional.of(invitation(
                        TeamInvitation.State.PENDING,
                        NOW.plusSeconds(60)
                )));
        when(memberships.find("team-1", "user-2"))
                .thenReturn(Optional.of(invited()));
        when(invitations.accept(INVITATION_ID, 0, NOW))
                .thenReturn(true);
        when(memberships.activate("team-1", "user-2", 0, NOW))
                .thenReturn(true);

        var accepted = useCase.accept("user-2", "raw-token");
        assertThat(accepted.state()).isEqualTo("ACTIVE");
        verify(outbox).append(any());

        when(invitations.findByTokenHash("token-hash"))
                .thenReturn(Optional.of(invitation(
                        TeamInvitation.State.ACCEPTED,
                        NOW.plusSeconds(60)
                )));
        when(memberships.find("team-1", "user-2"))
                .thenReturn(Optional.of(invited().activate(NOW)));
        assertThat(useCase.accept("user-2", "raw-token").state())
                .isEqualTo("ACTIVE");
    }

    @Test
    void malformedExpiredOrCrossUserInvitationIsHidden() {
        assertThatThrownBy(() -> useCase.accept("user-2", "bad"))
                .isInstanceOf(TeamInvitationInvalidException.class);

        when(tokens.isWellFormed("raw-token")).thenReturn(true);
        when(tokens.hash("raw-token")).thenReturn("token-hash");
        when(invitations.findByTokenHash("token-hash"))
                .thenReturn(Optional.of(invitation(
                        TeamInvitation.State.PENDING,
                        NOW.minusSeconds(1)
                )));
        assertThatThrownBy(() -> useCase.accept("other", "raw-token"))
                .isInstanceOf(TeamInvitationInvalidException.class);

        when(memberships.find("team-1", "user-2"))
                .thenReturn(Optional.of(invited()));
        assertThatThrownBy(() -> useCase.accept("user-2", "raw-token"))
                .isInstanceOf(TeamInvitationInvalidException.class);
    }

    @Test
    void ownerListsMembersAndCannotRemoveFinalOwner() {
        when(memberships.list("team-1"))
                .thenReturn(List.of(owner(), invited()));
        assertThat(useCase.list("owner-1", "team-1")).hasSize(2);

        when(memberships.revokeMember("team-1", "owner-1", 0))
                .thenReturn(
                        TeamMembershipRepository.RemovalResult.LAST_OWNER
                );
        assertThatThrownBy(() -> useCase.remove(
                "owner-1",
                "team-1",
                "owner-1",
                0
        )).isInstanceOf(TeamConflictException.class)
                .hasMessageContaining("final");
        verify(outbox, never()).append(any());
    }

    @Test
    void ownerRemovesMemberAndMissingMembershipIsHidden() {
        when(memberships.revokeMember("team-1", "user-2", 0))
                .thenReturn(TeamMembershipRepository.RemovalResult.REMOVED);
        useCase.remove("owner-1", "team-1", "user-2", 0);
        verify(outbox).append(any());

        when(memberships.revokeMember("team-1", "missing", 1))
                .thenReturn(
                        TeamMembershipRepository.RemovalResult
                                .NOT_FOUND_OR_CONFLICT
                );
        assertThatThrownBy(() -> useCase.remove(
                "owner-1",
                "team-1",
                "missing",
                1
        )).isInstanceOf(TeamNotFoundException.class);
    }

    @Test
    void ownerUpdatesAllowlistedPermissionsWithOptimisticVersion() {
        when(memberships.updatePermissions(
                "team-1",
                "user-2",
                0,
                Set.of("story:create", "story:edit")
        )).thenReturn(
                TeamMembershipRepository.PermissionUpdateResult.UPDATED
        );
        when(memberships.find("team-1", "user-2"))
                .thenReturn(Optional.of(activeMember(
                        Set.of("story:create", "story:edit"),
                        1
                )));

        var updated = useCase.updatePermissions(
                "owner-1",
                "team-1",
                "user-2",
                0,
                Set.of("story:create", "story:edit")
        );

        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.permissions())
                .containsExactlyInAnyOrder("story:create", "story:edit");
        verify(outbox).append(any());
    }

    @Test
    void permissionUpdateRejectsStaleInvalidAndMissingMembership() {
        when(memberships.updatePermissions(
                "team-1",
                "user-2",
                0,
                Set.of("story:create")
        )).thenReturn(
                TeamMembershipRepository.PermissionUpdateResult
                        .VERSION_CONFLICT
        );
        assertThatThrownBy(() -> useCase.updatePermissions(
                "owner-1",
                "team-1",
                "user-2",
                0,
                Set.of("story:create")
        )).isInstanceOf(TeamConflictException.class)
                .hasMessageContaining("Reload");

        assertThatThrownBy(() -> useCase.updatePermissions(
                "owner-1",
                "team-1",
                "user-2",
                0,
                Set.of("root:grant")
        )).isInstanceOf(TeamConflictException.class);

        when(memberships.updatePermissions(
                "team-1",
                "missing",
                0,
                Set.of("story:create")
        )).thenReturn(
                TeamMembershipRepository.PermissionUpdateResult.NOT_FOUND
        );
        assertThatThrownBy(() -> useCase.updatePermissions(
                "owner-1",
                "team-1",
                "missing",
                0,
                Set.of("story:create")
        )).isInstanceOf(TeamNotFoundException.class);
    }

    @Test
    void ownerCannotManageMembershipFromAnotherTeam() {
        Team otherTeam = new Team(
                "team-2",
                "other",
                "Other",
                "",
                "owner-2",
                Team.State.ACTIVE,
                NOW,
                NOW,
                0
        );
        when(teams.findById("team-2")).thenReturn(Optional.of(otherTeam));
        when(memberships.find("team-2", "owner-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.updatePermissions(
                "owner-1",
                "team-2",
                "user-2",
                0,
                Set.of("story:create")
        )).isInstanceOf(TeamAccessDeniedException.class);
        assertThatThrownBy(() -> useCase.remove(
                "owner-1",
                "team-2",
                "user-2",
                0
        )).isInstanceOf(TeamAccessDeniedException.class);
        verify(memberships, never()).updatePermissions(
                org.mockito.ArgumentMatchers.eq("team-2"),
                anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anySet()
        );
    }

    private static Team team() {
        return new Team(
                "team-1",
                "lam-da",
                "Lam Da",
                "",
                "owner-1",
                Team.State.ACTIVE,
                NOW,
                NOW,
                0
        );
    }

    private static TeamMembership owner() {
        return TeamMembership.owner("team-1", "owner-1", NOW);
    }

    private static TeamMembership invited() {
        return TeamMembership.invited(
                "team-1",
                "user-2",
                Set.of("story:create"),
                NOW
        );
    }

    private static TeamMembership activeMember(
            Set<String> permissions,
            long version
    ) {
        return new TeamMembership(
                "team-1:user-2",
                "team-1",
                "user-2",
                TeamMembership.Role.MEMBER,
                permissions,
                TeamMembership.State.ACTIVE,
                NOW,
                version
        );
    }

    private static TeamInvitation invitation(
            TeamInvitation.State state,
            Instant expiresAt
    ) {
        return new TeamInvitation(
                INVITATION_ID,
                "team-1",
                "user-2",
                "owner-1",
                Set.of("story:create"),
                "token-hash",
                KEY,
                state,
                expiresAt,
                expiresAt.isAfter(NOW)
                        ? NOW.minusSeconds(1)
                        : NOW.minus(Duration.ofDays(8)),
                state == TeamInvitation.State.ACCEPTED ? NOW : null,
                state == TeamInvitation.State.ACCEPTED ? 1 : 0
        );
    }

    private static IdentityUserDirectory.IdentityUser activeUser() {
        return new IdentityUserDirectory.IdentityUser(
                "user-2",
                "member@example.test",
                Set.of("USER"),
                "ACTIVE",
                NOW
        );
    }
}
