package com.storyplatform.shared.events.persistence;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("app.events.outbox.worker")
public record OutboxWorkerProperties(
        boolean enabled,
        @Min(1) @Max(500) int batchSize,
        @NotNull Duration leaseDuration,
        @NotNull Duration pollInterval,
        @Min(1) @Max(100) int maxAttempts,
        @NotNull Duration initialBackoff,
        @NotNull Duration maxBackoff
) {

    @AssertTrue(message = "worker durations and backoff range must be valid")
    public boolean isDurationPolicyValid() {
        return positive(leaseDuration)
                && positive(pollInterval)
                && positive(initialBackoff)
                && positive(maxBackoff)
                && initialBackoff.compareTo(maxBackoff) <= 0;
    }

    private static boolean positive(Duration duration) {
        return duration != null
                && !duration.isZero()
                && !duration.isNegative();
    }
}
