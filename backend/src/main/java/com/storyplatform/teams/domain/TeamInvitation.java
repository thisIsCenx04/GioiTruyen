package com.storyplatform.teams.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record TeamInvitation(
        String id,
        String teamId,
        String targetUserId,
        String invitedBy,
        Set<String> permissions,
        String tokenHash,
        String idempotencyKey,
        State state,
        Instant expiresAt,
        Instant createdAt,
        Instant acceptedAt,
        long version
) {
    public TeamInvitation {
        id = requireText(id, "id");
        teamId = requireText(teamId, "teamId");
        targetUserId = requireText(targetUserId, "targetUserId");
        invitedBy = requireText(invitedBy, "invitedBy");
        permissions = Set.copyOf(permissions);
        tokenHash = requireText(tokenHash, "tokenHash");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        state = Objects.requireNonNull(state, "state");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (!expiresAt.isAfter(createdAt) || version < 0) {
            throw new IllegalArgumentException(
                    "invalid invitation time or version"
            );
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public enum State {
        PENDING,
        ACCEPTED,
        EXPIRED,
        REVOKED
    }
}
