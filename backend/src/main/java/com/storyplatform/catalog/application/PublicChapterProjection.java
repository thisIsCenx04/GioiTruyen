package com.storyplatform.catalog.application;

import java.time.Instant;

public record PublicChapterProjection(
        String id,
        String storyId,
        int number,
        String slug,
        String title,
        Instant publishedAt,
        long version
) {
}
