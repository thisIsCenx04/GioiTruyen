package com.storyplatform.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "EMAIL_REQUIRED")
        @Size(max = 254, message = "EMAIL_INVALID")
        String email,
        @NotBlank(message = "PASSWORD_REQUIRED")
        @Size(min = 1, max = 128, message = "PASSWORD_INVALID")
        String password
) {
}
