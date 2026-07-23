package com.storyplatform.identity.infrastructure;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.identity.reauthentication")
public record ReauthenticationProperties(@NotNull Duration ttl) {

    public ReauthenticationProperties {
        if (ttl != null
                && (ttl.compareTo(Duration.ofMinutes(1)) < 0
                || ttl.compareTo(Duration.ofMinutes(15)) > 0)) {
            throw new IllegalArgumentException(
                    "Reauthentication TTL must be between 1 and 15 minutes"
            );
        }
    }
}
