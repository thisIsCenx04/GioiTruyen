package com.storyplatform.moderation.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.moderation.queue")
public record ModerationQueueProperties(Duration claimLease) {
}
