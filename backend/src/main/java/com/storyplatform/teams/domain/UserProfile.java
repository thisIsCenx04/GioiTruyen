package com.storyplatform.teams.domain;

import java.time.Instant;
import java.util.Objects;

public record UserProfile(
        String userId,
        String displayName,
        String bio,
        String avatarMediaId,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public UserProfile {
        userId = requireText(userId, "userId");
        displayName = requireText(displayName, "displayName");
        bio = bio == null ? "" : bio;
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (version < 0 || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("invalid profile version or time");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
