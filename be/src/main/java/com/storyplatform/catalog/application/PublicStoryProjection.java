package com.storyplatform.catalog.application;

import com.storyplatform.catalog.domain.Story;

import java.time.Instant;
import java.util.List;

public record PublicStoryProjection(
        String id,
        String teamId,
        String slug,
        String title,
        String synopsis,
        List<String> categoryIds,
        Story.Origin origin,
        String language,
        Story.CompletionStatus completionStatus,
        Instant publishedAt,
        Instant updatedAt,
        long version
) {
    public PublicStoryProjection {
        categoryIds = List.copyOf(categoryIds);
    }
}
