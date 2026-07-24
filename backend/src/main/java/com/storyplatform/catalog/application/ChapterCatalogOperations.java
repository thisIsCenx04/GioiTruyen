package com.storyplatform.catalog.application;

import java.util.List;

public interface ChapterCatalogOperations {

    ChapterPage list(String storyIdOrSlug, String cursor, int limit);

    record ChapterPage(
            List<PublicChapterProjection> items,
            String nextCursor,
            boolean hasMore
    ) {
        public ChapterPage {
            items = List.copyOf(items);
        }
    }
}
