package com.storyplatform.catalog.application;

import com.storyplatform.catalog.domain.Story;

import java.util.List;

public interface StoryCatalogOperations {

    StoryPage list(StoryFilter filter);

    PublicStoryProjection get(String idOrSlug);

    record StoryFilter(
            List<String> categorySlugs,
            Story.CompletionStatus completionStatus,
            Story.Origin origin,
            String teamId,
            String sort,
            String cursor,
            int limit
    ) {
        public StoryFilter {
            categorySlugs = List.copyOf(categorySlugs);
        }
    }

    record StoryPage(
            List<PublicStoryProjection> items,
            String nextCursor,
            boolean hasMore
    ) {
        public StoryPage {
            items = List.copyOf(items);
        }
    }
}
