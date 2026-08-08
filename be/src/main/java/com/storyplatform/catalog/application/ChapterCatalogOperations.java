package com.storyplatform.catalog.application;

import java.util.List;
import java.time.Instant;

public interface ChapterCatalogOperations {

    ChapterPage list(String storyIdOrSlug, String cursor, int limit);

    ChapterDetail detail(String chapterId);

    record ChapterPage(
            List<PublicChapterProjection> items,
            String nextCursor,
            boolean hasMore
    ) {
        public ChapterPage {
            items = List.copyOf(items);
        }
    }

    record ChapterDetail(
            String id,
            String storyId,
            int number,
            String slug,
            String title,
            String revisionId,
            long revisionNo,
            String contentHtml,
            int wordCount,
            Instant publishedAt,
            long version,
            String etag,
            ChapterLink previous,
            ChapterLink next
    ) {
    }

    record ChapterLink(
            String id,
            int number,
            String slug,
            String title
    ) {
    }
}
