package com.storyplatform.community.domain;

import java.time.Instant;
import java.util.Objects;

public record Comment(
        String id,
        TargetType targetType,
        String targetId,
        String parentId,
        String rootId,
        int depth,
        String authorId,
        String body,
        Status status,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static final int MAXIMUM_DEPTH = 2;

    public Comment {
        Objects.requireNonNull(id);
        Objects.requireNonNull(targetType);
        Objects.requireNonNull(targetId);
        Objects.requireNonNull(rootId);
        Objects.requireNonNull(authorId);
        Objects.requireNonNull(body);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        if (depth < 0 || depth > MAXIMUM_DEPTH || version < 1) {
            throw new IllegalArgumentException("invalid comment state");
        }
    }

    public enum TargetType {
        STORY,
        CHAPTER
    }

    public enum Status {
        VISIBLE,
        HIDDEN,
        DELETED
    }
}
