package com.storyplatform.identity.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.identity.access-token")
public record AccessTokenProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration ttl,
        @NotBlank String signingKey
) {
    public AccessTokenProperties {
        if (ttl != null
                && (ttl.compareTo(Duration.ofMinutes(1)) < 0
                || ttl.compareTo(Duration.ofMinutes(30)) > 0)) {
            throw new IllegalArgumentException(
                    "access token TTL must be between 1 and 30 minutes"
            );
        }
    }
}
