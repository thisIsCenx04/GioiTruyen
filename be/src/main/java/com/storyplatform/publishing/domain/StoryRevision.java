package com.storyplatform.publishing.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record StoryRevision(
        String id,
        String storyId,
        long revisionNo,
        Snapshot snapshot,
        String createdBy,
        String checksum,
        Instant createdAt
) {

    public StoryRevision {
        id = uuid(id, "id");
        storyId = uuid(storyId, "storyId");
        createdBy = uuid(createdBy, "createdBy");
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (revisionNo < 1
                || checksum == null
                || !checksum.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "revision number or checksum is invalid"
            );
        }
    }

    public record Snapshot(
            String title,
            String synopsis,
            StoryDraft.Origin origin,
            String language,
            List<String> categoryIds,
            String coverAssetId
    ) {
        public Snapshot {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(synopsis, "synopsis");
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(language, "language");
            categoryIds = List.copyOf(categoryIds);
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
}
