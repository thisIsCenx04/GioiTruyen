package com.storyplatform.identity.api;

import com.storyplatform.identity.application.IdentityService;
import com.storyplatform.identity.application.PasswordResetOutcome;
import com.storyplatform.shared.api.ApiException;
import com.storyplatform.shared.api.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/auth/password")
public class PasswordResetController {

    private static final Map<String, String> ACCEPTED =
            Map.of("status", "ACCEPTED");

    private final IdentityService identityService;

    public PasswordResetController(IdentityService identityService) {
        this.identityService = Objects.requireNonNull(
                identityService,
                "identityService"
        );
    }

    @PostMapping("/forgot")
    public ResponseEntity<Map<String, String>> forgot(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        identityService.requestPasswordReset(
                request.email(),
                CorrelationId.from(servletRequest)
        );
        return ResponseEntity.accepted().body(ACCEPTED);
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        PasswordResetOutcome outcome = identityService.resetPassword(
                request.token(),
                request.newPassword()
        );
        return switch (outcome) {
            case RESET -> ResponseEntity.noContent().build();
            case INVALID_OR_EXPIRED -> throw rejected(
                    "PASSWORD_RESET_INVALID",
                    "The reset token is invalid or expired."
            );
            case WEAK_PASSWORD -> throw rejected(
                    "PASSWORD_REJECTED",
                    "The password does not meet the password policy."
            );
        };
    }

    private static ApiException rejected(String code, String detail) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                code,
                "Password reset rejected",
                detail
        );
    }
}
