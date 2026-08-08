package com.storyplatform.discovery.application.port;

import com.storyplatform.discovery.application.SuggestionOperations;

import java.util.List;

public interface SuggestionRepository {

    SuggestionPage find(String prefix, String atlasCursor, int limit);

    record SuggestionPage(
            List<SuggestionOperations.Suggestion> items,
            String nextAtlasCursor,
            boolean hasMore
    ) {
        public SuggestionPage {
            items = List.copyOf(items);
        }
    }
}
