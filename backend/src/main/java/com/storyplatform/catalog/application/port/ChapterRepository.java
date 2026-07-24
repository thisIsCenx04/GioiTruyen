package com.storyplatform.catalog.application.port;

import com.storyplatform.catalog.application.PublicChapterProjection;

import java.util.List;

public interface ChapterRepository {

    List<PublicChapterProjection> findPublished(ChapterListQuery query);

    record ChapterListQuery(
            String storyId,
            Integer afterNumber,
            String afterId,
            int limit
    ) {
    }
}
