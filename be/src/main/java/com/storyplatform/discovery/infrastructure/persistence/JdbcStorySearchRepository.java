package com.storyplatform.discovery.infrastructure.persistence;

import com.storyplatform.discovery.application.HomeStorySummary;
import com.storyplatform.discovery.application.SearchOperations;
import com.storyplatform.discovery.application.port.StorySearchRepository;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class JdbcStorySearchRepository
        implements StorySearchRepository {

    private final JdbcClient jdbc;

    public JdbcStorySearchRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public SearchPage search(SearchQuery request) {
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT s.id, s.team_id, s.slug, s.title,
                       s.cover_asset_id, s.published_at
                FROM stories s
                """);
        Map<String, Object> parameters = new HashMap<>();
        if (request.categoryId() != null) {
            sql.append("""
                     JOIN story_categories sc ON sc.story_id = s.id
                    """);
        }
        sql.append("""
                 WHERE s.workflow_status = 'PUBLISHED'
                   AND (
                       LOWER(s.title) LIKE LOWER(:query)
                       OR LOWER(s.synopsis) LIKE LOWER(:query)
                       OR LOWER(s.author_name) LIKE LOWER(:query)
                   )
                """);
        parameters.put("query", "%" + request.text() + "%");
        if (request.categoryId() != null) {
            sql.append(" AND sc.category_id = :categoryId");
            parameters.put("categoryId", request.categoryId());
        }
        if (request.completionStatus() != null) {
            sql.append(" AND s.completion_status = :completionStatus");
            parameters.put(
                    "completionStatus",
                    request.completionStatus()
            );
        }
        if (request.origin() != null) {
            sql.append(" AND s.origin = :origin");
            parameters.put("origin", request.origin());
        }
        sql.append("""
                 ORDER BY s.published_at DESC, s.id DESC
                 LIMIT :limit
                """);
        parameters.put("limit", request.limit());
        List<SearchOperations.SearchHit> hits = jdbc.sql(sql.toString())
                .params(parameters)
                .query((result, rowNumber) ->
                        new SearchOperations.SearchHit(
                                new HomeStorySummary(
                                        result.getString("id"),
                                        result.getString("team_id"),
                                        result.getString("slug"),
                                        result.getString("title"),
                                        result.getString("cover_asset_id"),
                                        result.getTimestamp("published_at")
                                                .toInstant()
                                ),
                                1.0,
                                List.of()
                        ))
                .list();
        return new SearchPage(hits, null, false, Map.of());
    }
}
