package com.storyplatform.community.infrastructure.persistence;

import com.storyplatform.community.application.port.StoryRelationRepository;
import com.storyplatform.community.domain.StoryRelation;

public class DisabledStoryRelationRepository implements StoryRelationRepository {

    @Override
    public boolean storyIsPublished(String storyId) {
        return true;
    }

    @Override
    public boolean insertIfAbsent(StoryRelation relation) {
        return true;
    }

    @Override
    public boolean deleteIfPresent(String storyId, String userId, StoryRelation.Type type) {
        return true;
    }

    @Override
    public boolean exists(String storyId, String userId, StoryRelation.Type type) {
        return false;
    }

    @Override
    public long count(String storyId, StoryRelation.Type type) {
        return 0L;
    }
}
