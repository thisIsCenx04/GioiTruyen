package com.storyplatform.teams.application;

import com.storyplatform.identity.application.contract.IdentityUserDirectory;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import com.storyplatform.teams.application.port.TeamInvitationRepository;
import com.storyplatform.teams.application.port.TeamInvitationTokenCodec;
import com.storyplatform.teams.application.port.TeamMembershipRepository;
import com.storyplatform.teams.application.port.TeamRepository;
import com.storyplatform.teams.domain.Team;
import com.storyplatform.teams.domain.TeamInvitation;
import com.storyplatform.teams.domain.TeamMembership;
import com.storyplatform.teams.domain.TeamPermissions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class TeamMembershipUseCase
        implements TeamMembershipOperations {

    public static final String INVITED_EVENT = "teams.member.invited";
    public static final String ACCEPTED_EVENT = "teams.member.accepted";
    public static final String PERMISSIONS_EVENT =
            "teams.member.permissions.changed";
    public static final String REMOVED_EVENT = "teams.member.removed";

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._:-]{7,127}"
    );

    private final TeamRepository teams;
    private final TeamMembershipRepository memberships;
    private final TeamInvitationRepository invitations;
    private final IdentityUserDirectory identities;
    private final TeamInvitationTokenCodec tokens;
    private final OutboxAppender outbox;
    private final Duration invitationTtl;
    private final Clock clock;

    public TeamMembershipUseCase(
            TeamRepository teams,
            TeamMembershipRepository memberships,
            TeamInvitationRepository invitations,
            IdentityUserDirectory identities,
            TeamInvitationTokenCodec tokens,
            OutboxAppender outbox,
            Duration invitationTtl,
            Clock clock
    ) {
        this.teams = Objects.requireNonNull(teams, "teams");
        this.memberships = Objects.requireNonNull(
                memberships,
                "memberships"
        );
        this.invitations = Objects.requireNonNull(
                invitations,
                "invitations"
        );
        this.identities = Objects.requireNonNull(identities, "identities");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.invitationTtl = Objects.requireNonNull(
                invitationTtl,
                "invitationTtl"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public List<MembershipView> list(String actorId, String teamId) {
        requireOwner(actorId, teamId);
        return memberships.list(teamId).stream()
                .map(TeamMembershipUseCase::view)
                .toList();
    }

    @Override
    public MembershipView invite(
            String actorId,
            String teamId,
            String targetUserId,
            Set<String> permissions,
            String idempotencyKey
    ) {
        requireOwner(actorId, teamId);
        Set<String> requested = requirePermissions(permissions);
        String key = requireIdempotencyKey(idempotencyKey);

        var replay = invitations.findByIdempotencyKey(teamId, key);
        if (replay.isPresent()) {
            TeamInvitation prior = replay.get();
            if (!prior.targetUserId().equals(targetUserId)
                    || !prior.permissions().equals(requested)) {
                throw conflict(
                        "IDEMPOTENCY_KEY_REUSED",
                        "The idempotency key was used for another request."
                );
            }
            return view(memberships.find(teamId, targetUserId)
                    .orElseThrow(TeamInvitationInvalidException::new));
        }
        requireTarget(targetUserId);
        if (memberships.find(teamId, targetUserId).isPresent()) {
            throw conflict(
                    "TEAM_MEMBER_EXISTS",
                    "The user already has a Team membership."
            );
        }

        Instant now = clock.instant();
        Instant expiresAt = now.plus(invitationTtl);
        var issued = tokens.issue(targetUserId, expiresAt);
        TeamMembership membership = TeamMembership.invited(
                teamId,
                targetUserId,
                requested,
                now
        );
        TeamInvitation invitation = new TeamInvitation(
                issued.invitationId(),
                teamId,
                targetUserId,
                actorId,
                requested,
                issued.tokenHash(),
                key,
                TeamInvitation.State.PENDING,
                expiresAt,
                now,
                null,
                0
        );
        memberships.insert(membership);
        invitations.insert(invitation);
        append(
                UUID.fromString(invitation.id()),
                INVITED_EVENT,
                invitation.id(),
                actorId,
                teamId,
                new MemberInvited(invitation.id(), targetUserId, expiresAt),
                key,
                now
        );
        return view(membership);
    }

    @Override
    public MembershipView accept(String actorId, String rawToken) {
        if (!tokens.isWellFormed(rawToken)) {
            throw new TeamInvitationInvalidException();
        }
        TeamInvitation invitation = invitations.findByTokenHash(
                tokens.hash(rawToken)
        ).orElseThrow(TeamInvitationInvalidException::new);
        if (!invitation.targetUserId().equals(actorId)) {
            throw new TeamInvitationInvalidException();
        }
        if (identities.findById(actorId)
                .filter(IdentityUserDirectory.IdentityUser::active)
                .isEmpty()
                || teams.findById(invitation.teamId())
                .filter(team -> team.state() == Team.State.ACTIVE)
                .isEmpty()) {
            throw new TeamInvitationInvalidException();
        }
        TeamMembership membership = memberships.find(
                invitation.teamId(),
                actorId
        ).orElseThrow(TeamInvitationInvalidException::new);
        if (invitation.state() == TeamInvitation.State.ACCEPTED
                && membership.state() == TeamMembership.State.ACTIVE) {
            return view(membership);
        }

        Instant now = clock.instant();
        if (invitation.state() != TeamInvitation.State.PENDING
                || !invitation.expiresAt().isAfter(now)
                || !invitations.accept(
                        invitation.id(),
                        invitation.version(),
                        now
                )
                || !memberships.activate(
                        invitation.teamId(),
                        actorId,
                        membership.version(),
                        now
                )) {
            throw new TeamInvitationInvalidException();
        }
        append(
                UUID.randomUUID(),
                ACCEPTED_EVENT,
                membership.id(),
                actorId,
                invitation.teamId(),
                new MemberChanged(actorId),
                invitation.id(),
                now
        );
        return view(membership.activate(now));
    }

    @Override
    public MembershipView updatePermissions(
            String actorId,
            String teamId,
            String targetUserId,
            long version,
            Set<String> permissions
    ) {
        requireOwner(actorId, teamId);
        Set<String> requested = requirePermissions(permissions);
        TeamMembershipRepository.PermissionUpdateResult result =
                memberships.updatePermissions(
                        teamId,
                        targetUserId,
                        version,
                        requested
                );
        if (result == TeamMembershipRepository.PermissionUpdateResult
                .VERSION_CONFLICT) {
            throw conflict(
                    "TEAM_MEMBERSHIP_VERSION_CONFLICT",
                    "The membership changed. Reload before retrying."
            );
        }
        if (result != TeamMembershipRepository.PermissionUpdateResult
                .UPDATED) {
            throw new TeamNotFoundException();
        }
        Instant now = clock.instant();
        append(
                UUID.randomUUID(),
                PERMISSIONS_EVENT,
                teamId + ":" + targetUserId,
                actorId,
                teamId,
                new MemberChanged(targetUserId),
                UUID.randomUUID().toString(),
                now
        );
        return view(memberships.find(teamId, targetUserId)
                .orElseThrow(TeamNotFoundException::new));
    }

    @Override
    public void remove(
            String actorId,
            String teamId,
            String targetUserId,
            long version
    ) {
        requireOwner(actorId, teamId);
        TeamMembershipRepository.RemovalResult result =
                memberships.revokeMember(teamId, targetUserId, version);
        if (result == TeamMembershipRepository.RemovalResult.LAST_OWNER) {
            throw conflict(
                    "TEAM_LAST_OWNER",
                    "The final Team owner cannot be removed."
            );
        }
        if (result != TeamMembershipRepository.RemovalResult.REMOVED) {
            throw new TeamNotFoundException();
        }
        Instant now = clock.instant();
        append(
                UUID.randomUUID(),
                REMOVED_EVENT,
                teamId + ":" + targetUserId,
                actorId,
                teamId,
                new MemberChanged(targetUserId),
                UUID.randomUUID().toString(),
                now
        );
    }

    private void requireOwner(String actorId, String teamId) {
        Team team = teams.findById(teamId)
                .filter(item -> item.state() == Team.State.ACTIVE)
                .orElseThrow(TeamNotFoundException::new);
        TeamMembership membership = memberships.find(team.id(), actorId)
                .orElseThrow(TeamAccessDeniedException::new);
        if (membership.state() != TeamMembership.State.ACTIVE
                || membership.role() != TeamMembership.Role.OWNER) {
            throw new TeamAccessDeniedException();
        }
    }

    private void requireTarget(String targetUserId) {
        if (identities.findById(targetUserId)
                .filter(IdentityUserDirectory.IdentityUser::active)
                .isEmpty()) {
            throw conflict(
                    "TEAM_TARGET_UNAVAILABLE",
                    "The target user is unavailable."
            );
        }
    }

    private static Set<String> requirePermissions(Set<String> requested) {
        if (requested == null
                || requested.isEmpty()
                || !TeamPermissions.areMemberAssignable(requested)) {
            throw conflict(
                    "TEAM_PERMISSION_INVALID",
                    "One or more permissions are not allowed."
            );
        }
        return Set.copyOf(requested);
    }

    private static String requireIdempotencyKey(String key) {
        if (key == null || !IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw conflict(
                    "IDEMPOTENCY_KEY_INVALID",
                    "A valid Idempotency-Key header is required."
            );
        }
        return key;
    }

    private void append(
            UUID eventId,
            String eventType,
            String aggregateId,
            String actorId,
            String teamId,
            Object payload,
            String correlationId,
            Instant now
    ) {
        outbox.append(new IntegrationEvent(
                eventId,
                eventType,
                1,
                now,
                correlationId,
                "team_member",
                aggregateId,
                actorId,
                teamId,
                payload
        ));
    }

    private static TeamConflictException conflict(
            String code,
            String message
    ) {
        return new TeamConflictException(code, message);
    }

    private static MembershipView view(TeamMembership membership) {
        return new MembershipView(
                membership.teamId(),
                membership.userId(),
                membership.role().name(),
                membership.permissions(),
                membership.state().name(),
                membership.joinedAt(),
                membership.version()
        );
    }

    public record MemberInvited(
            String invitationId,
            String targetUserId,
            Instant expiresAt
    ) {
    }

    public record MemberChanged(String userId) {
    }
}
