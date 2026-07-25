package com.storyplatform.discovery.infrastructure.persistence;

import com.storyplatform.discovery.application.SuggestionOperations;
import com.storyplatform.discovery.application.port.SuggestionRepository;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Objects;

public final class JdbcSuggestionRepository
        implements SuggestionRepository {

    private final JdbcClient jdbc;

    public JdbcSuggestionRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public SuggestionPage find(
            String prefix,
            String cursor,
            int limit
    ) {
        List<SuggestionOperations.Suggestion> items = jdbc.sql("""
                        SELECT id, slug, title, cover_asset_id
                        FROM stories
                        WHERE workflow_status = 'PUBLISHED'
                          AND LOWER(title) LIKE LOWER(:prefix)
                        ORDER BY title, id
                        LIMIT :limit
                        """)
                .param("prefix", prefix + "%")
                .param("limit", limit)
                .query((result, rowNumber) ->
                        new SuggestionOperations.Suggestion(
                                result.getString("id"),
                                result.getString("slug"),
                                result.getString("title"),
                                result.getString("cover_asset_id")
                        ))
                .list();
        return new SuggestionPage(items, null, false);
    }
}
