package com.storyplatform.publishing.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ChapterDraft(
        String id,
        String storyId,
        String teamId,
        int number,
        String slug,
        String title,
        WorkflowStatus workflowStatus,
        String currentRevision,
        long currentRevisionNo,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public ChapterDraft {
        id = uuid(id, "id");
        storyId = uuid(storyId, "storyId");
        teamId = uuid(teamId, "teamId");
        currentRevision = uuid(currentRevision, "currentRevision");
        title = Objects.requireNonNull(title, "title");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (number < 1
                || slug == null
                || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                || slug.length() > 100
                || title.isBlank()
                || title.length() > 200
                || workflowStatus != WorkflowStatus.DRAFT
                || currentRevisionNo != 1
                || version != 1
                || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "chapter draft metadata is invalid"
            );
        }
    }

    private static String uuid(String value, String field) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    field + " must be a UUID",
                    exception
            );
        }
    }

    public enum WorkflowStatus {
        DRAFT
    }
}
