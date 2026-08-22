package com.storyplatform.teams.api;

import com.storyplatform.shared.api.ApiException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicTeamController {

    private final NamedParameterJdbcTemplate jdbc;

    public PublicTeamController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Published stories of one team, aggregated once.
     *
     * The directory needs four figures per team and the ranking table sorts on
     * three of them, so they are summed in a single grouped pass rather than as
     * four correlated sub-queries per row - the latter re-scans `stories` once
     * per team per column.
     *
     * published_at matters as well as the status: the catalog hides a story
     * that has no publish date, so counting on status alone let a team claim
     * more stories than it could ever list.
     */
    private static final String TEAM_TOTALS = """
            LEFT JOIN (
                SELECT s.team_id,
                       COUNT(*)                        AS story_count,
                       SUM(s.view_count_cache)         AS total_views,
                       SUM(s.favorite_count_cache)     AS total_favorites,
                       SUM(s.follow_count_cache)       AS total_follows
                FROM stories s
                WHERE s.status = 'PUBLISHED' AND s.published_at IS NOT NULL
                GROUP BY s.team_id
            ) agg ON agg.team_id = t.id
            """;

    private static final String TEAM_SELECT = """
            SELECT t.id, t.slug, t.name, t.description, t.status, t.avatar_url,
                   COALESCE(agg.story_count, 0)     AS story_count,
                   COALESCE(agg.total_views, 0)     AS total_views,
                   COALESCE(agg.total_favorites, 0) AS total_favorites,
                   COALESCE(agg.total_follows, 0)   AS total_follows
            FROM teams t
            """;

    private static TeamResponse readTeam(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TeamResponse(
                rs.getString("id"),
                rs.getString("slug"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("status"),
                1,
                rs.getString("avatar_url"),
                rs.getInt("story_count"),
                rs.getLong("total_views"),
                rs.getLong("total_favorites"),
                rs.getLong("total_follows")
        );
    }

    @GetMapping("/teams")
    public java.util.List<TeamResponse> listTeams() {
        // avatar_url and the published-story count let the directory show a real
        // team picture and a real figure. Without them the listing had nothing to
        // display but a name, which is why it grew decorative placeholder art.
        return jdbc.query(
                TEAM_SELECT + TEAM_TOTALS + """
                        WHERE t.status = 'ACTIVE'
                        ORDER BY story_count DESC, t.name ASC
                        """,
                Map.of(),
                (rs, rowNum) -> readTeam(rs)
        );
    }

    @GetMapping("/teams/{teamId}")
    public TeamResponse team(@PathVariable String teamId) {
        return jdbc.query(
                        TEAM_SELECT + TEAM_TOTALS + """
                                WHERE (t.id = :teamId OR t.slug = :teamId) AND t.status = 'ACTIVE'
                                LIMIT 1
                                """,
                        Map.of("teamId", teamId),
                        (rs, rowNum) -> readTeam(rs)
                ).stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "team.not_found",
                        "Team not found",
                        "Team not found"
                ));
    }

    public record TeamResponse(
            String id,
            String slug,
            String name,
            String description,
            String state,
            int version,
            /** Team picture, or null when the team has not set one. */
            String avatarUrl,
            /** Published stories, so the directory can rank and label teams. */
            int storyCount,
            /** Reads across every published story of the team. */
            long totalViews,
            /** Favourites across every published story of the team. */
            long totalFavorites,
            /** Follows across every published story of the team. */
            long totalFollows
    ) {
    }
}
