package com.storyplatform.identity.api;

import com.storyplatform.identity.application.ReauthenticationScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReauthenticationGrantRequest(
        @NotBlank
        @Size(max = 128)
        String password,
        @Size(max = 32)
        String mfaCode,
        @NotNull
        ReauthenticationScope scope,
        @NotBlank
        @Pattern(regexp = "^[a-z][a-z0-9_-]{0,63}$")
        String targetType,
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
        String targetId
) {
}
