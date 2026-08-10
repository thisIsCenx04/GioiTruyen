package com.storyplatform.auth.api;

import com.storyplatform.auth.application.UserAccountService;
import com.storyplatform.auth.application.dto.UpdateUserProfileRequest;
import com.storyplatform.auth.application.dto.UserDto;
import com.storyplatform.auth.application.dto.UserProfileResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserAccountController {

    private final UserAccountService userAccountService;

    public UserAccountController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal Jwt jwt) {
        return userAccountService.me(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/me/profile")
    public UserProfileResponse profile(@AuthenticationPrincipal Jwt jwt) {
        return userAccountService.profile(UUID.fromString(jwt.getSubject()));
    }

    @PatchMapping("/me/profile")
    public UserProfileResponse updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateUserProfileRequest request
    ) {
        return userAccountService.updateProfile(UUID.fromString(jwt.getSubject()), request);
    }
}
