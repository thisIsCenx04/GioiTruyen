package com.storyplatform.catalog.application;

import com.storyplatform.catalog.application.port.CatalogCursorCodec;
import com.storyplatform.catalog.application.port.StoryRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class StoryCatalogService implements StoryCatalogOperations {

    private final StoryRepository stories;
    private final CategoryOperations categories;
    private final CatalogCursorCodec cursors;

    public StoryCatalogService(
            StoryRepository stories,
            CategoryOperations categories,
            CatalogCursorCodec cursors
    ) {
        this.stories = Objects.requireNonNull(stories, "stories");
        this.categories = Objects.requireNonNull(categories, "categories");
        this.cursors = Objects.requireNonNull(cursors, "cursors");
    }

    @Override
    public StoryPage list(StoryFilter filter) {
        Objects.requireNonNull(filter, "filter");
        if (filter.limit() < 1 || filter.limit() > 50) {
            throw rejected("LIMIT_INVALID", "limit must be from 1 to 50");
        }
        if (filter.categorySlugs().size() > 10) {
            throw rejected(
                    "CATEGORY_FILTER_INVALID",
                    "at most 10 categories may be filtered"
            );
        }
        StoryRepository.Sort sort = parseSort(filter.sort());
        List<String> categoryIds = resolveCategoryIds(
                filter.categorySlugs()
        );
        String teamId = optionalUuid(filter.teamId(), "TEAM_ID_INVALID");
        Instant afterValue = null;
        String afterId = null;
        if (filter.cursor() != null && !filter.cursor().isBlank()) {
            CatalogCursorCodec.Cursor cursor;
            try {
                cursor = cursors.decode(filter.cursor());
            } catch (RuntimeException exception) {
                throw rejected("CURSOR_INVALID", "cursor is invalid");
            }
            if (cursor.sort() != sort) {
                throw rejected(
                        "CURSOR_SORT_MISMATCH",
                        "cursor does not match the requested sort"
                );
            }
            afterValue = cursor.value();
            afterId = cursor.id();
        }

        List<PublicStoryProjection> loaded = stories.findPublished(
                new StoryRepository.StoryListQuery(
                        categoryIds,
                        filter.completionStatus(),
                        filter.origin(),
                        teamId,
                        sort,
                        afterValue,
                        afterId,
                        filter.limit() + 1
                )
        );
        boolean hasMore = loaded.size() > filter.limit();
        List<PublicStoryProjection> items = hasMore
                ? loaded.subList(0, filter.limit())
                : loaded;
        String next = null;
        if (hasMore) {
            PublicStoryProjection last = items.getLast();
            Instant value = sort == StoryRepository.Sort.UPDATED_DESC
                    ? last.updatedAt()
                    : last.publishedAt();
            next = cursors.encode(new CatalogCursorCodec.Cursor(
                    sort,
                    value,
                    last.id()
            ));
        }
        return new StoryPage(items, next, hasMore);
    }

    @Override
    public PublicStoryProjection get(String idOrSlug) {
        if (idOrSlug == null || idOrSlug.isBlank()
                || idOrSlug.length() > 100) {
            throw rejected(
                    "STORY_IDENTIFIER_INVALID",
                    "story identifier is invalid"
            );
        }
        return stories.findPublishedByIdOrSlug(idOrSlug)
                .orElseThrow(() -> rejected(
                        "STORY_NOT_FOUND",
                        "published story was not found"
                ));
    }

    private List<String> resolveCategoryIds(List<String> slugs) {
        if (slugs.isEmpty()) {
            return List.of();
        }
        Map<String, String> ids = new HashMap<>();
        categories.getTaxonomy().groups().forEach(group ->
                group.categories().forEach(category ->
                        ids.put(category.slug(), category.id())));
        List<String> resolved = new ArrayList<>();
        for (String raw : slugs) {
            String slug = raw.toLowerCase(Locale.ROOT);
            String id = ids.get(slug);
            if (id == null || resolved.contains(id)) {
                throw rejected(
                        "CATEGORY_FILTER_INVALID",
                        "category filter is unknown or duplicated"
                );
            }
            resolved.add(id);
        }
        return List.copyOf(resolved);
    }

    private static StoryRepository.Sort parseSort(String value) {
        String normalized = value == null || value.isBlank()
                ? "published_desc"
                : value;
        return switch (normalized) {
            case "updated_desc" -> StoryRepository.Sort.UPDATED_DESC;
            case "published_desc" -> StoryRepository.Sort.PUBLISHED_DESC;
            default -> throw rejected(
                    "SORT_INVALID",
                    "sort must be updated_desc or published_desc"
            );
        };
    }

    private static String optionalUuid(String value, String code) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException exception) {
            throw rejected(code, "identifier must be a UUID");
        }
    }

    private static CatalogRequestException rejected(
            String code,
            String detail
    ) {
        return new CatalogRequestException(code, detail);
    }
}
