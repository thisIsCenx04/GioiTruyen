package com.storyplatform.publishing.infrastructure;

import com.storyplatform.publishing.application.ContentVisibilityOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalContentVisibilityOperations
        implements ContentVisibilityOperations {

    private final ContentVisibilityOperations delegate;

    public TransactionalContentVisibilityOperations(
            ContentVisibilityOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    @Transactional
    public VisibilityView teamChange(
            String actorId,
            String teamId,
            String storyId,
            long expectedVersion,
            VisibilityCommand command
    ) {
        return delegate.teamChange(
                actorId, teamId, storyId, expectedVersion, command
        );
    }

    @Override
    @Transactional
    public VisibilityView moderationChange(
            String actorId,
            String storyId,
            long expectedVersion,
            VisibilityCommand command
    ) {
        return delegate.moderationChange(
                actorId, storyId, expectedVersion, command
        );
    }
}
