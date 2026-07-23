package com.storyplatform.teams.api;

import com.storyplatform.shared.api.ApiException;
import com.storyplatform.teams.application.TeamConflictException;
import com.storyplatform.teams.application.TeamNotFoundException;
import com.storyplatform.teams.application.TeamOperations;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
public final class TeamController {

    private final TeamOperations teams;

    public TeamController(TeamOperations teams) {
        this.teams = Objects.requireNonNull(teams, "teams");
    }

    @GetMapping("/teams")
    public List<TeamOperations.TeamView> list(
            @RequestParam(defaultValue = "20") int limit
    ) {
        if (limit < 1 || limit > 50) {
            throw rejected(
                    HttpStatus.BAD_REQUEST,
                    "LIMIT_INVALID",
                    "The limit must be between 1 and 50."
            );
        }
        return teams.list(limit);
    }

    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamOperations.TeamView create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTeamRequest request
    ) {
        try {
            return teams.create(
                    jwt.getSubject(),
                    request.slug(),
                    request.name(),
                    request.description()
            );
        } catch (TeamConflictException exception) {
            throw conflict(exception);
        }
    }

    @GetMapping("/teams/{teamId}")
    public TeamOperations.TeamView get(@PathVariable String teamId) {
        try {
            return teams.get(requireTeamId(teamId));
        } catch (TeamNotFoundException exception) {
            throw notFound();
        }
    }

    @PatchMapping("/teams/{teamId}")
    public TeamOperations.TeamView update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String teamId,
            @Valid @RequestBody UpdateTeamRequest request
    ) {
        try {
            return teams.update(
                    jwt.getSubject(),
                    requireTeamId(teamId),
                    request.version(),
                    request.name(),
                    request.description()
            );
        } catch (TeamConflictException exception) {
            throw conflict(exception);
        } catch (TeamNotFoundException exception) {
            throw notFound();
        }
    }

    private static String requireTeamId(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException exception) {
            throw rejected(
                    HttpStatus.BAD_REQUEST,
                    "TEAM_ID_INVALID",
                    "The team identifier is invalid."
            );
        }
    }

    private static ApiException conflict(TeamConflictException exception) {
        return rejected(
                HttpStatus.CONFLICT,
                exception.code(),
                exception.getMessage()
        );
    }

    private static ApiException notFound() {
        return rejected(
                HttpStatus.NOT_FOUND,
                "TEAM_NOT_FOUND",
                "The requested team does not exist."
        );
    }

    private static ApiException rejected(
            HttpStatus status,
            String code,
            String detail
    ) {
        return new ApiException(
                status,
                code,
                "Team request rejected",
                detail
        );
    }
}
