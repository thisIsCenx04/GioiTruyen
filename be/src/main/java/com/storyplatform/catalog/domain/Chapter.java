package com.storyplatform.catalog.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record Chapter(
        String id,
        String storyId,
        String teamId,
        int number,
        String slug,
        String title,
        WorkflowStatus workflowStatus,
        String currentRevision,
        Instant scheduledAt,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    private static final Pattern SLUG = Pattern.compile(
            "[a-z0-9]+(?:-[a-z0-9]+)*"
    );

    public Chapter {
        id = uuid(id, "id");
        storyId = uuid(storyId, "storyId");
        teamId = uuid(teamId, "teamId");
        currentRevision = uuid(currentRevision, "currentRevision");
        if (number < 1 || slug == null || slug.length() > 100
                || !SLUG.matcher(slug).matches()
                || title == null || title.isBlank() || title.length() > 200) {
            throw new IllegalArgumentException("chapter metadata is invalid");
        }
        workflowStatus = Objects.requireNonNull(workflowStatus,
                "workflowStatus");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt) || version < 1) {
            throw new IllegalArgumentException(
                    "chapter version or timestamps are invalid"
            );
        }
        if ((workflowStatus == WorkflowStatus.PUBLISHED
                || workflowStatus == WorkflowStatus.HIDDEN)
                && publishedAt == null) {
            throw new IllegalArgumentException(
                    "published chapters require publishedAt"
            );
        }
    }

    private static String uuid(String value, String field) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must be a UUID",
                    exception);
        }
    }

    public enum WorkflowStatus {
        DRAFT,
        IN_REVIEW,
        CHANGES_REQUESTED,
        APPROVED,
        SCHEDULED,
        PUBLISHED,
        HIDDEN,
        ARCHIVED
    }
}
