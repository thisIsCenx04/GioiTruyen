package com.storyplatform.teams.api;

import com.storyplatform.shared.api.ApiException;
import com.storyplatform.teams.application.ProfileService;
import com.storyplatform.teams.application.ProfileNotFoundException;
import com.storyplatform.teams.application.ProfileVersionConflictException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

@RestController
public final class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
    }

    @GetMapping("/me")
    public ProfileService.PrivateProfile me(
            @AuthenticationPrincipal Jwt jwt
    ) {
        try {
            return profiles.privateProfile(jwt.getSubject());
        } catch (ProfileNotFoundException exception) {
            throw notFound();
        }
    }

    @PatchMapping("/me")
    public ProfileService.PrivateProfile update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        try {
            return profiles.update(
                    jwt.getSubject(),
                    request.version(),
                    request.displayName(),
                    request.bio(),
                    request.avatarMediaId()
            );
        } catch (ProfileVersionConflictException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PROFILE_VERSION_CONFLICT",
                    "Profile update rejected",
                    exception.getMessage()
            );
        } catch (ProfileNotFoundException exception) {
            throw notFound();
        }
    }

    @GetMapping("/users/{userId}")
    public ProfileService.PublicProfile publicProfile(
            @PathVariable String userId
    ) {
        try {
            return profiles.publicProfile(requireUserId(userId));
        } catch (ProfileNotFoundException exception) {
            throw notFound();
        }
    }

    private static String requireUserId(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "USER_ID_INVALID",
                    "Profile request rejected",
                    "The user identifier is invalid."
            );
        }
    }

    private static ApiException notFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "PROFILE_NOT_FOUND",
                "Profile not found",
                "The requested profile does not exist."
        );
    }
}
