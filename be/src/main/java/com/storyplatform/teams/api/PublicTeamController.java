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

    @GetMapping("/teams/{teamId}")
    public TeamResponse team(@PathVariable String teamId) {
        return jdbc.query(
                        """
                                SELECT id, slug, name, description, status
                                FROM teams
                                WHERE id = :teamId AND status = 'ACTIVE'
                                LIMIT 1
                                """,
                        Map.of("teamId", teamId),
                        (rs, rowNum) -> new TeamResponse(
                                rs.getString("id"),
                                rs.getString("slug"),
                                rs.getString("name"),
                                rs.getString("description"),
                                rs.getString("status"),
                                1
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
            int version
    ) {
    }
}
