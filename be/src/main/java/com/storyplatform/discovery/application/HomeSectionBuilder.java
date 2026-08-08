package com.storyplatform.discovery.application;

import com.storyplatform.discovery.application.port.HomeStorySource;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class HomeSectionBuilder {

    public static final int STORIES_PER_SECTION = 12;
    private static final int QUERY_LIMIT = STORIES_PER_SECTION + 1;

    private final HomeStorySource stories;

    public HomeSectionBuilder(HomeStorySource stories) {
        this.stories = Objects.requireNonNull(stories, "stories");
    }

    public HomeReadModel build(
            String locale,
            String version,
            Instant generatedAt
    ) {
        List<HomeStorySummary> latest = bounded(
                stories.find(HomeStorySource.Filter.LATEST, QUERY_LIMIT)
        );
        List<HomeStorySummary> completed = fallback(
                stories.find(HomeStorySource.Filter.COMPLETED, QUERY_LIMIT),
                latest
        );
        List<HomeStorySummary> original = fallback(
                stories.find(HomeStorySource.Filter.ORIGINAL, QUERY_LIMIT),
                latest
        );
        return new HomeReadModel(
                locale,
                version,
                generatedAt,
                List.of(
                        section("latest", HomeReadModel.SectionType.LATEST,
                                title(locale, "Mới cập nhật", "Latest"),
                                latest),
                        section("completed",
                                HomeReadModel.SectionType.COMPLETED,
                                title(locale, "Đã hoàn thành", "Completed"),
                                completed),
                        section("original",
                                HomeReadModel.SectionType.ORIGINAL,
                                title(locale, "Truyện sáng tác", "Originals"),
                                original)
                )
        );
    }

    private static HomeReadModel.HomeSection section(
            String id,
            HomeReadModel.SectionType type,
            String title,
            List<HomeStorySummary> stories
    ) {
        return new HomeReadModel.HomeSection(id, type, title, stories);
    }

    private static List<HomeStorySummary> fallback(
            List<HomeStorySummary> selected,
            List<HomeStorySummary> latest
    ) {
        List<HomeStorySummary> bounded = bounded(selected);
        return bounded.isEmpty() ? latest : bounded;
    }

    private static List<HomeStorySummary> bounded(
            List<HomeStorySummary> values
    ) {
        Objects.requireNonNull(values, "values");
        return List.copyOf(values.subList(
                0,
                Math.min(values.size(), STORIES_PER_SECTION)
        ));
    }

    private static String title(
            String locale,
            String vietnamese,
            String english
    ) {
        return locale.startsWith("vi") ? vietnamese : english;
    }
}
