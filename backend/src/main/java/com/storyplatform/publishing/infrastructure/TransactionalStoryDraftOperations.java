package com.storyplatform.publishing.infrastructure;

import com.storyplatform.publishing.application.StoryDraftOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalStoryDraftOperations
        implements StoryDraftOperations {

    private final StoryDraftOperations delegate;

    public TransactionalStoryDraftOperations(
            StoryDraftOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @Transactional
    public DraftView create(
            String actorId,
            String teamId,
            String idempotencyKey,
            CreateCommand command
    ) {
        return delegate.create(
                actorId,
                teamId,
                idempotencyKey,
                command
        );
    }
}
