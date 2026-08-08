package com.storyplatform.catalog.application;

import com.storyplatform.catalog.application.port.ChapterCursorCodec;
import com.storyplatform.catalog.application.port.ChapterRepository;
import com.storyplatform.catalog.application.port.StoryRepository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ChapterCatalogService implements ChapterCatalogOperations {

    private final StoryRepository stories;
    private final ChapterRepository chapters;
    private final ChapterCursorCodec cursors;

    public ChapterCatalogService(
            StoryRepository stories,
            ChapterRepository chapters,
            ChapterCursorCodec cursors
    ) {
        this.stories = Objects.requireNonNull(stories, "stories");
        this.chapters = Objects.requireNonNull(chapters, "chapters");
        this.cursors = Objects.requireNonNull(cursors, "cursors");
    }

    @Override
    public ChapterPage list(
            String storyIdOrSlug,
            String cursorValue,
            int limit
    ) {
        if (storyIdOrSlug == null || storyIdOrSlug.isBlank()
                || storyIdOrSlug.length() > 100) {
            throw rejected("STORY_IDENTIFIER_INVALID",
                    "story identifier is invalid");
        }
        if (limit < 1 || limit > 100) {
            throw rejected("LIMIT_INVALID", "limit must be from 1 to 100");
        }
        PublicStoryProjection story = stories
                .findPublishedByIdOrSlug(storyIdOrSlug)
                .orElseThrow(() -> rejected("STORY_NOT_FOUND",
                        "published story was not found"));
        Integer afterNumber = null;
        String afterId = null;
        if (cursorValue != null && !cursorValue.isBlank()) {
            ChapterCursorCodec.Cursor cursor;
            try {
                cursor = cursors.decode(cursorValue);
            } catch (RuntimeException exception) {
                throw rejected("CURSOR_INVALID", "cursor is invalid");
            }
            if (!story.id().equals(cursor.storyId())) {
                throw rejected("CURSOR_STORY_MISMATCH",
                        "cursor does not belong to this story");
            }
            afterNumber = cursor.number();
            afterId = cursor.id();
        }
        List<PublicChapterProjection> loaded = chapters.findPublished(
                new ChapterRepository.ChapterListQuery(
                        story.id(),
                        afterNumber,
                        afterId,
                        limit + 1
                )
        );
        boolean hasMore = loaded.size() > limit;
        List<PublicChapterProjection> items = hasMore
                ? loaded.subList(0, limit)
                : loaded;
        String next = null;
        if (hasMore) {
            PublicChapterProjection last = items.getLast();
            next = cursors.encode(new ChapterCursorCodec.Cursor(
                    story.id(),
                    last.number(),
                    last.id()
            ));
        }
        return new ChapterPage(items, next, hasMore);
    }

    @Override
    public ChapterDetail detail(String chapterId) {
        String id;
        try {
            id = UUID.fromString(chapterId).toString();
        } catch (RuntimeException exception) {
            throw rejected(
                    "CHAPTER_IDENTIFIER_INVALID",
                    "chapter identifier is invalid"
            );
        }
        ChapterRepository.StoredChapter stored = chapters
                .findPublishedDetail(id)
                .orElseThrow(() -> rejected(
                        "CHAPTER_NOT_FOUND",
                        "published chapter was not found"
                ));
        PublicChapterProjection chapter = stored.chapter();
        if (stories.findPublishedByIdOrSlug(chapter.storyId()).isEmpty()) {
            throw rejected(
                    "CHAPTER_NOT_FOUND",
                    "published chapter was not found"
            );
        }
        return new ChapterDetail(
                chapter.id(),
                chapter.storyId(),
                chapter.number(),
                chapter.slug(),
                chapter.title(),
                stored.revisionId(),
                stored.revisionNo(),
                stored.contentHtml(),
                wordCount(stored.plainText()),
                chapter.publishedAt(),
                chapter.version(),
                stored.checksum(),
                chapters.previous(
                        chapter.storyId(),
                        chapter.number(),
                        chapter.id()
                ).map(ChapterCatalogService::link).orElse(null),
                chapters.next(
                        chapter.storyId(),
                        chapter.number(),
                        chapter.id()
                ).map(ChapterCatalogService::link).orElse(null)
        );
    }

    private static ChapterLink link(PublicChapterProjection chapter) {
        return new ChapterLink(
                chapter.id(),
                chapter.number(),
                chapter.slug(),
                chapter.title()
        );
    }

    private static int wordCount(String plainText) {
        return plainText == null || plainText.isBlank()
                ? 0
                : plainText.trim().split("\\s+").length;
    }

    private static CatalogRequestException rejected(
            String code,
            String detail
    ) {
        return new CatalogRequestException(code, detail);
    }
}
