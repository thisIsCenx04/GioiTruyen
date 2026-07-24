package com.storyplatform.catalog.application.port;

import com.storyplatform.catalog.application.PublicChapterProjection;

import java.util.List;
import java.util.Optional;

public interface ChapterRepository {

    List<PublicChapterProjection> findPublished(ChapterListQuery query);

    Optional<StoredChapter> findPublishedDetail(String chapterId);

    Optional<PublicChapterProjection> previous(
            String storyId,
            int number,
            String chapterId
    );

    Optional<PublicChapterProjection> next(
            String storyId,
            int number,
            String chapterId
    );

    record ChapterListQuery(
            String storyId,
            Integer afterNumber,
            String afterId,
            int limit
    ) {
    }

    record StoredChapter(
            PublicChapterProjection chapter,
            String revisionId,
            long revisionNo,
            String contentHtml,
            String plainText,
            String checksum
    ) {
    }
}
