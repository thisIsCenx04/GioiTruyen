package com.storyplatform.publishing.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public record StoryDraft(
        String id,
        String teamId,
        String slug,
        String title,
        String synopsis,
        List<String> categoryIds,
        Origin origin,
        String language,
        CompletionStatus completionStatus,
        WorkflowStatus workflowStatus,
        String currentRevision,
        String coverAssetId,
        Instant createdAt,
        Instant updatedAt,
        long version
) {

    private static final Pattern SLUG = Pattern.compile(
            "[a-z0-9]+(?:-[a-z0-9]+)*"
    );
    private static final Pattern LANGUAGE = Pattern.compile(
            "[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})*"
    );

    public StoryDraft {
        id = uuid(id, "id");
        teamId = uuid(teamId, "teamId");
        currentRevision = uuid(currentRevision, "currentRevision");
        if (coverAssetId != null) {
            coverAssetId = uuid(coverAssetId, "coverAssetId");
        }
        if (slug == null || !SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException("slug is invalid");
        }
        if (title == null || title.isBlank() || title.length() > 200
                || synopsis == null
                || synopsis.isBlank()
                || synopsis.length() > 5000) {
            throw new IllegalArgumentException("story metadata is invalid");
        }
        if (language == null || !LANGUAGE.matcher(language).matches()) {
            throw new IllegalArgumentException("language is invalid");
        }
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(completionStatus, "completionStatus");
        Objects.requireNonNull(workflowStatus, "workflowStatus");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        categoryIds = List.copyOf(categoryIds);
        Set<String> uniqueCategories = new HashSet<>(categoryIds);
        if (categoryIds.isEmpty()
                || categoryIds.size() > 30
                || uniqueCategories.size() != categoryIds.size()) {
            throw new IllegalArgumentException("taxonomy is invalid");
        }
        categoryIds.forEach(value -> uuid(value, "categoryId"));
        if (version < 1
                || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("draft state is invalid");
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

    public enum Origin {
        ORIGINAL,
        TRANSLATED
    }

    public enum CompletionStatus {
        ONGOING,
        COMPLETED,
        HIATUS
    }

    public enum WorkflowStatus {
        DRAFT,
        CHANGES_REQUESTED
    }
}
