package com.storyplatform.publishing.application.port;

import com.storyplatform.publishing.domain.ChapterDraft;
import com.storyplatform.publishing.domain.ChapterRevision;

public interface ChapterDraftRepository {

    boolean numberExists(String storyId, int number);

    void insert(ChapterDraft chapter, ChapterRevision revision);
}
