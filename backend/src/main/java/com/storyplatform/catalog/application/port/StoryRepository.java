package com.storyplatform.catalog.application.port;

import com.storyplatform.catalog.application.PublicStoryProjection;
import com.storyplatform.catalog.domain.Story;

import java.util.Optional;

public interface StoryRepository {

    boolean insertIfSlugAvailable(Story story);

    Optional<PublicStoryProjection> findPublishedByIdOrSlug(String value);
}
