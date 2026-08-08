package com.storyplatform.identity.api;

import com.storyplatform.identity.application.EmailVerificationOutcome;
import com.storyplatform.identity.application.IdentityService;
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
@RequestMapping("/auth/email")
public class EmailVerificationController {

    private final IdentityService identityService;

    public EmailVerificationController(IdentityService identityService) {
        this.identityService = Objects.requireNonNull(
                identityService,
                "identityService"
        );
    }

    @PostMapping("/verify")
    public ResponseEntity<Void> verify(
            @Valid @RequestBody EmailVerificationRequest request
    ) {
        EmailVerificationOutcome outcome =
                identityService.verifyEmail(request.token());
        if (outcome == EmailVerificationOutcome.INVALID_OR_EXPIRED) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VERIFICATION_TOKEN_INVALID",
                    "Email verification failed",
                    "The verification token is invalid or expired."
            );
        }
        return ResponseEntity.noContent().build();
    }
}
