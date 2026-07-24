package com.storyplatform.publishing.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.publishing.due-worker")
public record PublishingDueProperties(
        Duration pollInterval,
        Duration leaseDuration,
        Duration inactiveRetryDelay
) {
    public PublishingDueProperties {
        pollInterval = value(pollInterval, Duration.ofSeconds(2));
        leaseDuration = value(leaseDuration, Duration.ofSeconds(30));
        inactiveRetryDelay = value(
                inactiveRetryDelay,
                Duration.ofMinutes(5)
        );
    }

    private static Duration value(
            Duration candidate,
            Duration defaultValue
    ) {
        return candidate == null ? defaultValue : candidate;
    }
}
