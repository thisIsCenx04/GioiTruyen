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

    @GetMapping("/teams")
    public java.util.List<TeamResponse> listTeams() {
        // avatar_url and the published-story count let the directory show a real
        // team picture and a real figure. Without them the listing had nothing to
        // display but a name, which is why it grew decorative placeholder art.
        return jdbc.query(
                """
                        SELECT t.id, t.slug, t.name, t.description, t.status, t.avatar_url,
                               (SELECT COUNT(*) FROM stories s
                                 WHERE s.team_id = t.id AND s.status = 'PUBLISHED') AS story_count
                        FROM teams t
                        WHERE t.status = 'ACTIVE'
                        ORDER BY story_count DESC, t.name ASC
                        """,
                Map.of(),
                (rs, rowNum) -> new TeamResponse(
                        rs.getString("id"),
                        rs.getString("slug"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("status"),
                        1,
                        rs.getString("avatar_url"),
                        rs.getInt("story_count")
                )
        );
    }

    @GetMapping("/teams/{teamId}")
    public TeamResponse team(@PathVariable String teamId) {
        return jdbc.query(
                        """
                                SELECT t.id, t.slug, t.name, t.description, t.status, t.avatar_url,
                                       (SELECT COUNT(*) FROM stories s
                                         WHERE s.team_id = t.id AND s.status = 'PUBLISHED') AS story_count
                                FROM teams t
                                WHERE (t.id = :teamId OR t.slug = :teamId) AND t.status = 'ACTIVE'
                                LIMIT 1
                                """,
                        Map.of("teamId", teamId),
                        (rs, rowNum) -> new TeamResponse(
                                rs.getString("id"),
                                rs.getString("slug"),
                                rs.getString("name"),
                                rs.getString("description"),
                                rs.getString("status"),
                                1,
                                rs.getString("avatar_url"),
                                rs.getInt("story_count")
                        )
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
            int storyCount
    ) {
    }
}
