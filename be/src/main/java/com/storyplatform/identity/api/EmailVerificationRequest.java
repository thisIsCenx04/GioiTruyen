package com.storyplatform.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmailVerificationRequest(
        @NotBlank(message = "TOKEN_REQUIRED")
        @Size(max = 128, message = "TOKEN_INVALID")
        @Pattern(
                regexp = "^[A-Za-z0-9._-]+$",
                message = "TOKEN_INVALID"
        )
        String token
) {
}
