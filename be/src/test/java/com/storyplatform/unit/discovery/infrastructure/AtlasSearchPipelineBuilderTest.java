package com.storyplatform.unit.discovery.infrastructure;

import com.storyplatform.discovery.application.port.StorySearchRepository;
import com.storyplatform.discovery.infrastructure.persistence
        .AtlasSearchPipelineBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AtlasSearchPipelineBuilderTest {

    @Test
    void buildsAllowlistedFiltersFacetsHighlightsAndSearchAfter() {
        var query = new StorySearchRepository.SearchQuery(
                "\"}]} $where regex.*",
                "10000000-0000-4000-8000-000000000001",
                "COMPLETED",
                "ORIGINAL",
                "atlas-token",
                20
        );
        var builder = new AtlasSearchPipelineBuilder("story_search_v1");

        String results = builder.results(query).toString();
        String facets = builder.facets(query).toString();

        assertThat(results)
                .contains("$search")
                .contains("workflowStatus")
                .contains("PUBLISHED")
                .contains("searchAfter=atlas-token")
                .contains("searchHighlights")
                .doesNotContain("$regex");
        assertThat(facets)
                .contains("$searchMeta")
                .contains("categoryIds")
                .contains("completionStatus")
                .contains("origin");
    }

    @Test
    void rejectsUnsafeIndexNamesAndSupportsUnfilteredSearch() {
        assertThatThrownBy(() ->
                new AtlasSearchPipelineBuilder("$bad.index"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new AtlasSearchPipelineBuilder("safe_index").results(
                new StorySearchRepository.SearchQuery(
                        "story", null, null, null, null, 10
                )
        ).toString()).doesNotContain("searchAfter");
    }
}
