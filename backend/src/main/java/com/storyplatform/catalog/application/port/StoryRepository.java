package com.storyplatform.catalog.application.port;

import com.storyplatform.catalog.application.PublicStoryProjection;
import com.storyplatform.catalog.domain.Story;

import java.util.Optional;
import java.util.List;
import java.time.Instant;

public interface StoryRepository {

    boolean insertIfSlugAvailable(Story story);

    Optional<PublicStoryProjection> findPublishedByIdOrSlug(String value);

    List<PublicStoryProjection> findPublished(StoryListQuery query);

    record StoryListQuery(
            List<String> categoryIds,
            Story.CompletionStatus completionStatus,
            Story.Origin origin,
            String teamId,
            Sort sort,
            Instant afterValue,
            String afterId,
            int limit
    ) {
        public StoryListQuery {
            categoryIds = List.copyOf(categoryIds);
        }
    }

    enum Sort {
        UPDATED_DESC("updatedAt"),
        PUBLISHED_DESC("publishedAt");

        private final String field;

        Sort(String field) {
            this.field = field;
        }

        public String field() {
            return field;
        }
    }
}
