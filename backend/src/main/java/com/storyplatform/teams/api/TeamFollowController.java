package com.storyplatform.teams.api;

import com.storyplatform.shared.api.ApiException;
import com.storyplatform.teams.application.TeamFollowOperations;
import com.storyplatform.teams.application.TeamNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

@RestController
public final class TeamFollowController {

    private final TeamFollowOperations follows;

    public TeamFollowController(TeamFollowOperations follows) {
        this.follows = Objects.requireNonNull(follows, "follows");
    }

    @GetMapping("/teams/{teamId}/follow")
    public TeamFollowOperations.FollowView status(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String teamId
    ) {
        try {
            return follows.status(jwt.getSubject(), uuid(teamId));
        } catch (TeamNotFoundException exception) {
            throw notFound();
        }
    }

    @PutMapping("/teams/{teamId}/follow")
    public TeamFollowOperations.FollowView follow(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String teamId
    ) {
        try {
            return follows.follow(jwt.getSubject(), uuid(teamId));
        } catch (TeamNotFoundException exception) {
            throw notFound();
        }
    }

    @DeleteMapping("/teams/{teamId}/follow")
    public TeamFollowOperations.FollowView unfollow(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String teamId
    ) {
        try {
            return follows.unfollow(jwt.getSubject(), uuid(teamId));
        } catch (TeamNotFoundException exception) {
            throw notFound();
        }
    }

    private static String uuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TEAM_ID_INVALID",
                    "Team follow request rejected",
                    "The Team identifier is invalid."
            );
        }
    }

    private static ApiException notFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "TEAM_NOT_FOUND",
                "Team follow request rejected",
                "The requested Team does not exist."
        );
    }
}
