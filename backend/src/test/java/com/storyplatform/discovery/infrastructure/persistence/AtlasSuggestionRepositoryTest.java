package com.storyplatform.discovery.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AtlasSuggestionRepositoryTest {

    @Test
    void buildsPublishedAutocompleteWithBoundedSearchAfter() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        var repository = new AtlasSuggestionRepository(
                mongo,
                "story_search_v1"
        );

        String first = repository.pipeline("story", null, 9).toString();
        String next = repository.pipeline(
                "story",
                "atlas-token",
                9
        ).toString();

        assertThat(first)
                .contains("autocomplete")
                .contains("workflowStatus")
                .contains("PUBLISHED")
                .contains("$limit=9")
                .doesNotContain("$regex")
                .doesNotContain("searchAfter");
        assertThat(next).contains("searchAfter=atlas-token");
    }

    @Test
    void rejectsUnsafeIndexNamesAndTextFallbackIsEmpty() {
        assertThatThrownBy(() -> new AtlasSuggestionRepository(
                mock(MongoTemplate.class),
                "$bad"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThat(new DisabledSuggestionRepository()
                .find("story", null, 8).items()).isEmpty();
    }
}
