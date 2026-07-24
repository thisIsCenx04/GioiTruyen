package com.storyplatform.discovery.application.port;

import com.storyplatform.discovery.application.SearchOperations;

import java.util.List;
import java.util.Map;

public interface StorySearchRepository {

    SearchPage search(SearchQuery query);

    record SearchQuery(
            String text,
            String categoryId,
            String completionStatus,
            String origin,
            String atlasCursor,
            int limit
    ) {
    }

    record SearchPage(
            List<SearchOperations.SearchHit> items,
            String nextAtlasCursor,
            boolean hasMore,
            Map<String, Map<String, Long>> facets
    ) {
        public SearchPage {
            items = List.copyOf(items);
            facets = Map.copyOf(facets);
        }
    }
}
