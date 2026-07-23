package com.storyplatform.identity.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotBlank(message = "EMAIL_REQUIRED")
        @Size(max = 254, message = "EMAIL_TOO_LONG")
        String email,
        @NotBlank(message = "PASSWORD_REQUIRED")
        @Size(
                min = 12,
                max = 128,
                message = "PASSWORD_LENGTH_INVALID"
        )
        String password,
        @NotNull(message = "CONSENT_REQUIRED")
        @AssertTrue(message = "CONSENT_REQUIRED")
        Boolean acceptedTerms,
        @NotBlank(message = "CONSENT_VERSION_REQUIRED")
        @Pattern(
                regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}",
                message = "CONSENT_VERSION_INVALID"
        )
        String consentVersion
) {
}
