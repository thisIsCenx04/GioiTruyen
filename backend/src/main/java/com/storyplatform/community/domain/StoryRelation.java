package com.storyplatform.community.domain;

import java.time.Instant;
import java.util.Objects;

public record StoryRelation(
        String id,
        String storyId,
        String userId,
        Type type,
        Instant createdAt
) {
    public StoryRelation {
        Objects.requireNonNull(id);
        Objects.requireNonNull(storyId);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(createdAt);
    }

    public static StoryRelation create(
            String storyId,
            String userId,
            Type type,
            Instant now
    ) {
        return new StoryRelation(
                storyId + ":" + userId + ":" + type.name(),
                storyId,
                userId,
                type,
                now
        );
    }

    public enum Type {
        FAVORITE,
        FOLLOW
    }
}
