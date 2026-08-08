package com.storyplatform.community.application.port;

import com.storyplatform.community.domain.StoryRelation;

public interface StoryRelationRepository {

    boolean storyIsPublished(String storyId);

    boolean insertIfAbsent(StoryRelation relation);

    boolean deleteIfPresent(
            String storyId,
            String userId,
            StoryRelation.Type type
    );

    boolean exists(
            String storyId,
            String userId,
            StoryRelation.Type type
    );

    long count(String storyId, StoryRelation.Type type);
}
