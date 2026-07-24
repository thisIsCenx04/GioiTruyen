package com.storyplatform.publishing.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ChapterRevision(
        String id,
        String chapterId,
        long revisionNo,
        String contentHtml,
        String plainText,
        String checksum,
        String createdBy,
        Instant createdAt
) {
    public ChapterRevision {
        id = uuid(id, "id");
        chapterId = uuid(chapterId, "chapterId");
        createdBy = uuid(createdBy, "createdBy");
        contentHtml = Objects.requireNonNull(contentHtml, "contentHtml");
        plainText = Objects.requireNonNull(plainText, "plainText");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (revisionNo < 1
                || contentHtml.isBlank()
                || plainText.isBlank()
                || checksum == null
                || !checksum.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "chapter revision content is invalid"
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
}
