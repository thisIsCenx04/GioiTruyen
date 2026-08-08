package com.storyplatform.identity.domain;

import java.time.Instant;
import java.util.Objects;

public record EmailVerification(
        String id,
        String userId,
        String tokenHash,
        Instant expiresAt,
        Instant consumedAt,
        Instant createdAt
) {

    public EmailVerification {
        id = requireText(id, "id");
        userId = requireText(userId, "userId");
        tokenHash = requireText(tokenHash, "tokenHash");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException(
                    "expiresAt must be after createdAt"
            );
        }
        if (consumedAt != null && consumedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "consumedAt must not be before createdAt"
            );
        }
    }

    public static EmailVerification pending(
            String id,
            String userId,
            String tokenHash,
            Instant expiresAt,
            Instant createdAt
    ) {
        return new EmailVerification(
                id,
                userId,
                tokenHash,
                expiresAt,
                null,
                createdAt
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
