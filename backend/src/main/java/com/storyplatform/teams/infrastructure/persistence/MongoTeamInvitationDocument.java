package com.storyplatform.teams.infrastructure.persistence;

import com.storyplatform.teams.domain.TeamInvitation;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Set;

@Document(collection = MongoTeamInvitationDocument.COLLECTION)
public record MongoTeamInvitationDocument(
        @Id String id,
        String teamId,
        String targetUserId,
        String invitedBy,
        Set<String> permissions,
        String tokenHash,
        String idempotencyKey,
        TeamInvitation.State state,
        Instant expiresAt,
        Instant createdAt,
        Instant acceptedAt,
        long version
) {
    public static final String COLLECTION = "team_invitations";

    static MongoTeamInvitationDocument from(TeamInvitation invitation) {
        return new MongoTeamInvitationDocument(
                invitation.id(),
                invitation.teamId(),
                invitation.targetUserId(),
                invitation.invitedBy(),
                invitation.permissions(),
                invitation.tokenHash(),
                invitation.idempotencyKey(),
                invitation.state(),
                invitation.expiresAt(),
                invitation.createdAt(),
                invitation.acceptedAt(),
                invitation.version()
        );
    }

    TeamInvitation toDomain() {
        return new TeamInvitation(
                id,
                teamId,
                targetUserId,
                invitedBy,
                permissions,
                tokenHash,
                idempotencyKey,
                state,
                expiresAt,
                createdAt,
                acceptedAt,
                version
        );
    }
}
