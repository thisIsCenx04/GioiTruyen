package com.storyplatform.teams.api;

import com.storyplatform.shared.api.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TeamApplicationController {

    private final JdbcClient jdbc;

    public TeamApplicationController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/teams/applications/me")
    public List<TeamApplicationResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return jdbc.sql("""
                        SELECT id, slug, name, description, state,
                               submitted_at, reviewed_at
                        FROM team_applications
                        WHERE requester_user_id = :userId
                        ORDER BY submitted_at DESC
                        LIMIT 20
                        """)
                .param("userId", jwt.getSubject())
                .query((result, rowNumber) -> response(result, rowNumber))
                .list();
    }

    @PostMapping("/teams/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamApplicationResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTeamApplicationRequest request
    ) {
        Instant now = Instant.now();
        String id = UUID.randomUUID().toString();
        try {
            jdbc.sql("""
                            INSERT INTO team_applications (
                                id, requester_user_id, slug, name, description,
                                state, submitted_at, version
                            ) VALUES (
                                :id, :requesterUserId, :slug, :name,
                                :description, 'PENDING_REVIEW', :submittedAt, 0
                            )
                            """)
                    .param("id", id)
                    .param("requesterUserId", jwt.getSubject())
                    .param("slug", request.slug().strip())
                    .param("name", request.name().strip())
                    .param("description", request.description().strip())
                    .param("submittedAt", now)
                    .update();
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "TEAM_APPLICATION_SLUG_TAKEN",
                    "Team application rejected",
                    "The requested team slug is already in review or already used."
            );
        }
        return new TeamApplicationResponse(
                id,
                request.slug().strip(),
                request.name().strip(),
                request.description().strip(),
                "PENDING_REVIEW",
                now.toString(),
                null
        );
    }

    private static TeamApplicationResponse response(ResultSet result, int rowNumber)
            throws SQLException {
        return new TeamApplicationResponse(
                result.getString("id"),
                result.getString("slug"),
                result.getString("name"),
                result.getString("description"),
                result.getString("state"),
                timestamp(result, "submitted_at"),
                timestamp(result, "reviewed_at")
        );
    }

    private static String timestamp(ResultSet result, String column)
            throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant().toString();
    }

    public record CreateTeamApplicationRequest(
            @NotBlank
            @Size(min = 3, max = 80)
            @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*")
            String slug,
            @NotBlank
            @Size(min = 2, max = 160)
            String name,
            @Size(max = 2000)
            String description
    ) {
        public CreateTeamApplicationRequest {
            description = description == null ? "" : description;
        }
    }

    public record TeamApplicationResponse(
            String id,
            String slug,
            String name,
            String description,
            String state,
            String submittedAt,
            String reviewedAt
    ) {
    }
}
