package com.storyplatform.publishing.infrastructure;

import com.storyplatform.publishing.application.ChapterDraftOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalChapterDraftOperations
        implements ChapterDraftOperations {

    private final ChapterDraftOperations delegate;

    public TransactionalChapterDraftOperations(
            ChapterDraftOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @Transactional
    public ChapterView create(
            String actorId,
            String teamId,
            String storyId,
            CreateCommand command
    ) {
        return delegate.create(actorId, teamId, storyId, command);
    }
}
