package com.storyplatform.identity.infrastructure;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.identity.login-risk")
public record LoginRiskProperties(
        @Min(1) @Max(100) int maxEmailFailures,
        @Min(1) @Max(1000) int maxAddressFailures,
        @NotNull Duration window,
        @NotBlank String hmacKey
) {
    public LoginRiskProperties {
        if (window != null
                && (window.compareTo(Duration.ofMinutes(1)) < 0
                || window.compareTo(Duration.ofHours(24)) > 0)) {
            throw new IllegalArgumentException(
                    "login risk window must be between 1 minute and 24 hours"
            );
        }
    }
}
