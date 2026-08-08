package com.storyplatform.teams.domain;

import java.time.Instant;
import java.util.Objects;

public record TeamFollow(
        String id,
        String teamId,
        String userId,
        Instant createdAt
) {
    public TeamFollow {
        id = requireText(id, "id");
        teamId = requireText(teamId, "teamId");
        userId = requireText(userId, "userId");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static TeamFollow create(
            String teamId,
            String userId,
            Instant now
    ) {
        return new TeamFollow(
                teamId + ":" + userId,
                teamId,
                userId,
                now
        );
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
