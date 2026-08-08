package com.storyplatform.identity.infrastructure;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.identity.mfa")
public record MfaProperties(
        @NotBlank String encryptionKey
) {
}
