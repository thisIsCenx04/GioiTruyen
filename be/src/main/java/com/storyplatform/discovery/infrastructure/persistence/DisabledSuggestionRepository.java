package com.storyplatform.discovery.infrastructure.persistence;

import com.storyplatform.discovery.application.port.SuggestionRepository;

import java.util.List;

public final class DisabledSuggestionRepository
        implements SuggestionRepository {

    @Override
    public SuggestionPage find(
            String prefix,
            String atlasCursor,
            int limit
    ) {
        return new SuggestionPage(List.of(), null, false);
    }
}
