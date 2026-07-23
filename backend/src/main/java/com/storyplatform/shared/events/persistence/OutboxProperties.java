package com.storyplatform.shared.events.persistence;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.events.outbox")
public record OutboxProperties(
        @Min(1024) @Max(1_048_576) int maxPayloadBytes
) {
}
