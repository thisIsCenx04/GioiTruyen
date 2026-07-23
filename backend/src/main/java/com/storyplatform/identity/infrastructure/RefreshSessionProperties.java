package com.storyplatform.identity.infrastructure;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.identity.refresh-session")
public record RefreshSessionProperties(
        @NotNull Duration ttl,
        @Min(10) @Max(2000) int maximumGenerations
) {
    public RefreshSessionProperties {
        if (ttl != null
                && (ttl.compareTo(Duration.ofHours(1)) < 0
                || ttl.compareTo(Duration.ofDays(90)) > 0)) {
            throw new IllegalArgumentException(
                    "refresh session TTL must be between 1 hour and 90 days"
            );
        }
    }
}
