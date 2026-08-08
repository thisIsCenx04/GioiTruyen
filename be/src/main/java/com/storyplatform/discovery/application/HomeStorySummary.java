package com.storyplatform.discovery.application;

import java.time.Instant;

public record HomeStorySummary(
        String id,
        String teamId,
        String slug,
        String title,
        String coverAssetId,
        Instant publishedAt
) {
}
