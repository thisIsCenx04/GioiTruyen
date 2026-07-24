package com.storyplatform.publishing.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.publishing.prechecks")
public record PublishingPrecheckProperties(
        boolean enabled,
        Duration leaseDuration,
        Duration timeout,
        Duration pollInterval
) {
}
