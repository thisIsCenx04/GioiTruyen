package com.storyplatform.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RefreshSessionRequest(
        @NotBlank(message = "REFRESH_TOKEN_REQUIRED")
        @Size(min = 43, max = 128, message = "REFRESH_TOKEN_INVALID")
        @Pattern(
                regexp = "^[A-Za-z0-9_-]+$",
                message = "REFRESH_TOKEN_INVALID"
        )
        String refreshToken
) {
}
