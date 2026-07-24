package com.storyplatform.community.application;

import com.storyplatform.community.domain.StoryRelation;

public interface StoryRelationOperations {

    RelationView status(
            String userId,
            String storyId,
            StoryRelation.Type type
    );

    RelationView add(
            String userId,
            String storyId,
            StoryRelation.Type type
    );

    RelationView remove(
            String userId,
            String storyId,
            StoryRelation.Type type
    );

    record RelationView(
            String storyId,
            String type,
            boolean active,
            long count
    ) {
    }
}
