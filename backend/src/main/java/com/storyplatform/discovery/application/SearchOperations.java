package com.storyplatform.discovery.application;

import java.util.List;
import java.util.Map;

public interface SearchOperations {

    SearchResponse search(SearchRequest request);

    record SearchRequest(
            String query,
            String categoryId,
            String completionStatus,
            String origin,
            String cursor,
            int limit
    ) {
    }

    record SearchResponse(
            List<SearchHit> items,
            String nextCursor,
            boolean hasMore,
            Map<String, Map<String, Long>> facets,
            long tookMs
    ) {
        public SearchResponse {
            items = List.copyOf(items);
            facets = Map.copyOf(facets);
        }
    }

    record SearchHit(
            HomeStorySummary story,
            double score,
            List<String> highlights
    ) {
        public SearchHit {
            highlights = List.copyOf(highlights);
        }
    }
}
