package com.storyplatform.teams.domain;

import java.time.Instant;
import java.util.Objects;

public record Team(
        String id,
        String slug,
        String name,
        String description,
        String ownerUserId,
        State state,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public Team {
        id = requireText(id, "id");
        slug = requireText(slug, "slug");
        name = requireText(name, "name");
        description = description == null ? "" : description;
        ownerUserId = requireText(ownerUserId, "ownerUserId");
        state = Objects.requireNonNull(state, "state");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (version < 0 || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("invalid team version or time");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public enum State {
        ACTIVE,
        SUSPENDED,
        ARCHIVED
    }
}
