package com.storyplatform.teams.api;

import com.storyplatform.shared.api.ApiException;
import com.storyplatform.teams.application.TeamAccessDeniedException;
import com.storyplatform.teams.application.TeamConflictException;
import com.storyplatform.teams.application.TeamInvitationInvalidException;
import com.storyplatform.teams.application.TeamMembershipOperations;
import com.storyplatform.teams.application.TeamNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
public final class TeamMembershipController {

    private final TeamMembershipOperations memberships;

    public TeamMembershipController(
            TeamMembershipOperations memberships
    ) {
        this.memberships = Objects.requireNonNull(
                memberships,
                "memberships"
        );
    }

    @GetMapping("/teams/{teamId}/members")
    public List<TeamMembershipOperations.MembershipView> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String teamId
    ) {
        try {
            return memberships.list(jwt.getSubject(), uuid(teamId));
        } catch (TeamAccessDeniedException exception) {
            throw forbidden();
        } catch (TeamNotFoundException exception) {
            throw notFound();
        }
    }

    @PostMapping("/teams/{teamId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamMembershipOperations.MembershipView invite(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String teamId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AddTeamMemberRequest request
    ) {
        try {
            return memberships.invite(
                    jwt.getSubject(),
                    uuid(teamId),
                    uuid(request.userId()),
                    request.permissions(),
                    idempotencyKey
            );
        } catch (TeamAccessDeniedException exception) {
            throw forbidden();
        } catch (TeamNotFoundException exception) {
            throw notFound();
        } catch (TeamConflictException exception) {
            throw conflict(exception);
        }
    }

    @PostMapping("/team-invitations/{token}/accept")
    public TeamMembershipOperations.MembershipView accept(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String token
    ) {
        try {
            return memberships.accept(jwt.getSubject(), token);
        } catch (TeamInvitationInvalidException exception) {
            throw rejected(
                    HttpStatus.NOT_FOUND,
                    "TEAM_INVITATION_INVALID",
                    exception.getMessage()
            );
        }
    }

    @DeleteMapping("/teams/{teamId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String teamId,
            @PathVariable String userId,
            @RequestParam long version
    ) {
        try {
            memberships.remove(
                    jwt.getSubject(),
                    uuid(teamId),
                    uuid(userId),
                    version
            );
        } catch (TeamAccessDeniedException exception) {
            throw forbidden();
        } catch (TeamNotFoundException exception) {
            throw notFound();
        } catch (TeamConflictException exception) {
            throw conflict(exception);
        }
    }

    @PatchMapping("/teams/{teamId}/members/{userId}/permissions")
    public TeamMembershipOperations.MembershipView updatePermissions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String teamId,
            @PathVariable String userId,
            @Valid @RequestBody UpdateTeamPermissionsRequest request
    ) {
        try {
            return memberships.updatePermissions(
                    jwt.getSubject(),
                    uuid(teamId),
                    uuid(userId),
                    request.version(),
                    request.permissions()
            );
        } catch (TeamAccessDeniedException exception) {
            throw forbidden();
        } catch (TeamNotFoundException exception) {
            throw notFound();
        } catch (TeamConflictException exception) {
            throw conflict(exception);
        }
    }

    private static String uuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException exception) {
            throw rejected(
                    HttpStatus.BAD_REQUEST,
                    "IDENTIFIER_INVALID",
                    "A Team or user identifier is invalid."
            );
        }
    }

    private static ApiException forbidden() {
        return rejected(
                HttpStatus.FORBIDDEN,
                "TEAM_OWNER_REQUIRED",
                "An active Team owner membership is required."
        );
    }

    private static ApiException notFound() {
        return rejected(
                HttpStatus.NOT_FOUND,
                "TEAM_RESOURCE_NOT_FOUND",
                "The requested Team resource does not exist."
        );
    }

    private static ApiException conflict(TeamConflictException exception) {
        return rejected(
                HttpStatus.CONFLICT,
                exception.code(),
                exception.getMessage()
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
                "Team membership request rejected",
                detail
        );
    }
}
