package com.storyplatform.identity.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.identity.verification")
public record VerificationProperties(
        @NotNull
        Duration tokenTtl,
        @NotBlank
        String hmacKey
) {
    public VerificationProperties {
        if (tokenTtl != null
                && (tokenTtl.compareTo(Duration.ofHours(1)) < 0
                || tokenTtl.compareTo(Duration.ofDays(7)) > 0)) {
            throw new IllegalArgumentException(
                    "tokenTtl must be between 1 hour and 7 days"
            );
        }
    }
}
