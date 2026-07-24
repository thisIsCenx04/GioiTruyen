package com.storyplatform.discovery.application;

import java.util.List;

public interface SuggestionOperations {

    SuggestionResponse suggest(SuggestionRequest request);

    record SuggestionRequest(
            String query,
            String cursor,
            int limit,
            String rateLimitSubject
    ) {
    }

    record SuggestionResponse(
            List<Suggestion> items,
            String nextCursor,
            boolean hasMore
    ) {
        public SuggestionResponse {
            items = List.copyOf(items);
        }
    }

    record Suggestion(
            String id,
            String slug,
            String title,
            String coverAssetId
    ) {
    }
}
