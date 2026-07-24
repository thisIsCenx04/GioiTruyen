package com.storyplatform.community.infrastructure;

import com.storyplatform.community.application.ReactionOperations;
import com.storyplatform.community.domain.Reaction;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalReactionOperations
        implements ReactionOperations {

    private final ReactionOperations delegate;

    public TransactionalReactionOperations(ReactionOperations delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    @Transactional(readOnly = true)
    public ReactionView status(
            String actorId,
            Reaction.TargetType targetType,
            String targetId
    ) {
        return delegate.status(actorId, targetType, targetId);
    }

    @Override
    @Transactional
    public ReactionView add(
            String actorId,
            Reaction.TargetType targetType,
            String targetId
    ) {
        return delegate.add(actorId, targetType, targetId);
    }

    @Override
    @Transactional
    public ReactionView remove(
            String actorId,
            Reaction.TargetType targetType,
            String targetId
    ) {
        return delegate.remove(actorId, targetType, targetId);
    }
}
