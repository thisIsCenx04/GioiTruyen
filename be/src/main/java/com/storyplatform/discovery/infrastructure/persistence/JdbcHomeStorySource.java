package com.storyplatform.discovery.infrastructure.persistence;

import com.storyplatform.discovery.application.HomeStorySummary;
import com.storyplatform.discovery.application.port.HomeStorySource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class JdbcHomeStorySource implements HomeStorySource {

    private final JdbcClient jdbc;

    public JdbcHomeStorySource(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public List<HomeStorySummary> find(Filter filter, int limit) {
        String predicate = switch (filter) {
            case LATEST -> "";
            case COMPLETED -> " AND completion_status = 'COMPLETED'";
            case ORIGINAL -> " AND origin = 'ORIGINAL'";
        };
        return jdbc.sql("""
                        SELECT id, team_id, slug, title, cover_asset_id,
                               published_at
                        FROM stories
                        WHERE workflow_status = 'PUBLISHED'
                        """ + predicate + """
                         ORDER BY published_at DESC, id DESC
                         LIMIT :limit
                        """)
                .param("limit", limit)
                .query((result, rowNumber) -> new HomeStorySummary(
                        result.getString("id"),
                        result.getString("team_id"),
                        result.getString("slug"),
                        result.getString("title"),
                        result.getString("cover_asset_id"),
                        result.getTimestamp("published_at").toInstant()
                ))
                .list();
    }
}
