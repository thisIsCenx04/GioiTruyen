package com.storyplatform.unit.discovery.infrastructure;

import com.storyplatform.discovery.application.HomeStorySummary;
import com.storyplatform.discovery.application.port.StorySearchRepository;
import com.storyplatform.discovery.infrastructure.persistence
        .TextStorySearchRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TextStorySearchRepositoryTest {

    @Test
    void usesTextIndexAllowlistedFiltersAndPublicProjection() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.find(
                any(Query.class),
                eq(HomeStorySummary.class),
                eq("stories")
        )).thenReturn(List.of(story()));
        var repository = new TextStorySearchRepository(mongo);

        var result = repository.search(query(
                "category",
                "COMPLETED",
                "ORIGINAL"
        ));

        ArgumentCaptor<Query> captured =
                ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(
                captured.capture(),
                eq(HomeStorySummary.class),
                eq("stories")
        );
        assertThat(captured.getValue().getQueryObject().toString())
                .contains("$text")
                .contains("workflowStatus=PUBLISHED")
                .contains("completionStatus=COMPLETED")
                .contains("origin=ORIGINAL")
                .doesNotContain("$regex");
        assertThat(captured.getValue().getFieldsObject().keySet())
                .doesNotContain("synopsis", "currentRevision");
        assertThat(result.items()).hasSize(1);
        assertThat(result.hasMore()).isFalse();
    }

    @Test
    void supportsSearchWithoutOptionalFilters() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.find(
                any(Query.class),
                eq(HomeStorySummary.class),
                eq("stories")
        )).thenReturn(List.of());

        assertThat(new TextStorySearchRepository(mongo)
                .search(query(null, null, null)).items()).isEmpty();
    }

    private static StorySearchRepository.SearchQuery query(
            String category,
            String status,
            String origin
    ) {
        return new StorySearchRepository.SearchQuery(
                "story",
                category,
                status,
                origin,
                null,
                20
        );
    }

    private static HomeStorySummary story() {
        return new HomeStorySummary(
                "10000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                "story",
                "Story",
                null,
                Instant.EPOCH
        );
    }
}
