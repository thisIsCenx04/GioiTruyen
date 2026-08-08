package com.storyplatform.identity.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record RefreshTokenFamily(
        String id,
        String userId,
        long securityVersion,
        String currentTokenHash,
        List<String> usedTokenHashes,
        int generation,
        Instant expiresAt,
        Instant revokedAt,
        String revokeReason,
        Instant createdAt,
        Instant updatedAt
) {

    public RefreshTokenFamily {
        id = requireText(id, "id");
        userId = requireText(userId, "userId");
        currentTokenHash = requireText(
                currentTokenHash,
                "currentTokenHash"
        );
        usedTokenHashes = List.copyOf(Objects.requireNonNull(
                usedTokenHashes,
                "usedTokenHashes"
        ));
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (securityVersion < 1 || generation < 0) {
            throw new IllegalArgumentException(
                    "securityVersion must be positive and generation valid"
            );
        }
        if (!expiresAt.isAfter(createdAt)
                || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "refresh family timestamps are inconsistent"
            );
        }
        if ((revokedAt == null) != (revokeReason == null)) {
            throw new IllegalArgumentException(
                    "revocation time and reason must be set together"
            );
        }
    }

    public static RefreshTokenFamily active(
            String id,
            String userId,
            long securityVersion,
            String tokenHash,
            Instant expiresAt,
            Instant now
    ) {
        return new RefreshTokenFamily(
                id,
                userId,
                securityVersion,
                tokenHash,
                List.of(),
                0,
                expiresAt,
                null,
                null,
                now,
                now
        );
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }
        return value;
    }
}
