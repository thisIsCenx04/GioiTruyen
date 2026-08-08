package com.storyplatform.community.domain;

import java.time.Instant;
import java.util.Objects;

public record Reaction(
        String id,
        TargetType targetType,
        String targetId,
        String actorId,
        Instant createdAt
) {
    public Reaction {
        Objects.requireNonNull(id);
        Objects.requireNonNull(targetType);
        Objects.requireNonNull(targetId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(createdAt);
    }

    public static Reaction create(
            TargetType targetType,
            String targetId,
            String actorId,
            Instant now
    ) {
        return new Reaction(
                targetType + ":" + targetId + ":" + actorId,
                targetType,
                targetId,
                actorId,
                now
        );
    }

    public enum TargetType {
        STORY,
        CHAPTER,
        COMMENT
    }
}
