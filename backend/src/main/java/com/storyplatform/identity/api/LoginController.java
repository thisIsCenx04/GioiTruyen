package com.storyplatform.identity.api;

import com.storyplatform.identity.application.IdentityService;
import com.storyplatform.identity.application.LoginCommand;
import com.storyplatform.identity.application.LoginOutcome;
import com.storyplatform.identity.application.LoginRiskUnavailableException;
import com.storyplatform.shared.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.time.Duration;

@RestController
@RequestMapping("/auth")
public class LoginController {

    private final IdentityService identityService;

    public LoginController(IdentityService identityService) {
        this.identityService = Objects.requireNonNull(
                identityService,
                "identityService"
        );
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        LoginOutcome outcome;
        try {
            outcome = identityService.login(new LoginCommand(
                    request.email(),
                    request.password(),
                    servletRequest.getRemoteAddr()
            ));
        } catch (LoginRiskUnavailableException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "LOGIN_RISK_UNAVAILABLE",
                    "Login temporarily unavailable",
                    "Please try again later."
            );
        }

        return switch (outcome.status()) {
            case AUTHENTICATED -> ResponseEntity.ok(
                    LoginResponse.bearer(
                            outcome.accessToken(),
                            outcome.expiresInSeconds()
                    )
            );
            case INVALID_CREDENTIALS -> throw rejected(
                    HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_FAILED"
            );
            case RATE_LIMITED -> throw rejected(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "LOGIN_RATE_LIMITED",
                    Duration.ofSeconds(outcome.retryAfterSeconds())
            );
        };
    }

    private static ApiException rejected(
            HttpStatus status,
            String code
    ) {
        return rejected(status, code, null);
    }

    private static ApiException rejected(
            HttpStatus status,
            String code,
            Duration retryAfter
    ) {
        return new ApiException(
                status,
                code,
                "Login rejected",
                "The login request could not be accepted.",
                retryAfter
        );
    }
}
