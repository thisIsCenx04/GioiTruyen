package com.storyplatform.identity.api;

import com.storyplatform.identity.application.IdentityService;
import com.storyplatform.identity.application.RefreshSessionOutcome;
import com.storyplatform.shared.api.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/auth")
public class RefreshSessionController {

    private final IdentityService identityService;

    public RefreshSessionController(IdentityService identityService) {
        this.identityService = Objects.requireNonNull(
                identityService,
                "identityService"
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @Valid @RequestBody RefreshSessionRequest request
    ) {
        RefreshSessionOutcome outcome = identityService.refreshSession(
                request.refreshToken()
        );
        if (outcome.status() != RefreshSessionOutcome.Status.ROTATED) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "REFRESH_TOKEN_INVALID",
                    "Session refresh rejected",
                    "The refresh token is invalid or no longer usable."
            );
        }
        return ResponseEntity.ok(LoginResponse.bearer(
                outcome.accessToken(),
                outcome.expiresInSeconds(),
                outcome.refreshToken()
        ));
    }
}
