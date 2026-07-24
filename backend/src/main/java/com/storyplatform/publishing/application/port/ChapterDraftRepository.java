package com.storyplatform.publishing.application.port;

import com.storyplatform.publishing.domain.ChapterDraft;
import com.storyplatform.publishing.domain.ChapterRevision;

import java.util.Optional;

public interface ChapterDraftRepository {

    boolean numberExists(String storyId, int number);

    void insert(ChapterDraft chapter, ChapterRevision revision);

    Optional<StoredChapter> findOwned(
            String teamId,
            String storyId,
            String chapterId
    );

    boolean update(
            ChapterDraft chapter,
            ChapterRevision revision,
            long expectedVersion
    );

    record StoredChapter(ChapterDraft chapter) {
    }
}
