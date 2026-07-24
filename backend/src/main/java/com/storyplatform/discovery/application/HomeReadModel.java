package com.storyplatform.discovery.application;

import java.time.Instant;
import java.util.List;

public record HomeReadModel(
        String locale,
        String version,
        Instant generatedAt,
        List<HomeSection> sections
) {
    public HomeReadModel {
        sections = List.copyOf(sections);
    }

    public record HomeSection(
            String id,
            SectionType type,
            String title,
            List<HomeStorySummary> stories
    ) {
        public HomeSection {
            stories = List.copyOf(stories);
        }
    }

    public enum SectionType {
        LATEST,
        COMPLETED,
        ORIGINAL
    }
}
