package com.storyplatform.catalog.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public record Story(
        String id,
        String teamId,
        String slug,
        String title,
        List<String> aliases,
        String synopsis,
        List<String> categoryIds,
        Origin origin,
        String language,
        CompletionStatus completionStatus,
        WorkflowStatus workflowStatus,
        String currentRevision,
        String coverAssetId,
        Instant publishedAt,
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

    public Story {
        id = uuid(id, "id");
        teamId = uuid(teamId, "teamId");
        currentRevision = uuid(currentRevision, "currentRevision");
        if (coverAssetId != null) {
            coverAssetId = uuid(coverAssetId, "coverAssetId");
        }
        slug = text(slug, "slug", 100);
        title = text(title, "title", 200);
        synopsis = text(synopsis, "synopsis", 5000);
        origin = Objects.requireNonNull(origin, "origin");
        completionStatus = Objects.requireNonNull(
                completionStatus,
                "completionStatus"
        );
        workflowStatus = Objects.requireNonNull(
                workflowStatus,
                "workflowStatus"
        );
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (!SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException(
                    "slug must be lowercase kebab-case"
            );
        }
        if (language == null || !LANGUAGE.matcher(language).matches()) {
            throw new IllegalArgumentException(
                    "language must be a bounded BCP 47 tag"
            );
        }
        aliases = boundedUnique(aliases, "aliases", 10, 200, false);
        categoryIds = boundedUnique(
                categoryIds,
                "categoryIds",
                30,
                36,
                true
        );
        if (updatedAt.isBefore(createdAt) || version < 1) {
            throw new IllegalArgumentException(
                    "story version or timestamps are invalid"
            );
        }
        if (workflowStatus == WorkflowStatus.PUBLISHED
                && publishedAt == null) {
            throw new IllegalArgumentException(
                    "published stories require publishedAt"
            );
        }
    }

    public boolean publiclyVisible() {
        return workflowStatus == WorkflowStatus.PUBLISHED;
    }

    private static List<String> boundedUnique(
            List<String> values,
            String field,
            int maximumItems,
            int maximumLength,
            boolean uuids
    ) {
        Objects.requireNonNull(values, field);
        if (values.size() > maximumItems) {
            throw new IllegalArgumentException(field + " has too many items");
        }
        Set<String> normalized = new HashSet<>();
        List<String> checkedValues = new ArrayList<>();
        for (String value : values) {
            String checked = uuids
                    ? uuid(value, field)
                    : text(value, field, maximumLength);
            String key = checked.toLowerCase(Locale.ROOT);
            if (!normalized.add(key)) {
                throw new IllegalArgumentException(
                        field + " contains duplicates"
                );
            }
            checkedValues.add(checked);
        }
        return List.copyOf(checkedValues);
    }

    private static String text(
            String value,
            String field,
            int maximumLength
    ) {
        if (value == null || value.isBlank()
                || value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
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
        IN_REVIEW,
        CHANGES_REQUESTED,
        APPROVED,
        PUBLISHED,
        SUSPENDED,
        ARCHIVED
    }
}
