package com.storyplatform.community.infrastructure;

import com.storyplatform.community.application.StoryRelationOperations;
import com.storyplatform.community.domain.StoryRelation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalStoryRelationOperations
        implements StoryRelationOperations {

    private final StoryRelationOperations delegate;

    public TransactionalStoryRelationOperations(
            StoryRelationOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    @Transactional(readOnly = true)
    public RelationView status(
            String userId,
            String storyId,
            StoryRelation.Type type
    ) {
        return delegate.status(userId, storyId, type);
    }

    @Override
    @Transactional
    public RelationView add(
            String userId,
            String storyId,
            StoryRelation.Type type
    ) {
        return delegate.add(userId, storyId, type);
    }

    @Override
    @Transactional
    public RelationView remove(
            String userId,
            String storyId,
            StoryRelation.Type type
    ) {
        return delegate.remove(userId, storyId, type);
    }
}
