package com.storyplatform.bootstrap.persistence.migration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("app.mongodb.migrations")
public record MongoMigrationProperties(
        boolean enabled,
        boolean dryRun,
        @NotNull Duration lockDuration
) {

    @AssertTrue(message = "lockDuration must be at least 30 seconds")
    public boolean isLockDurationSafe() {
        return lockDuration != null
                && lockDuration.compareTo(Duration.ofSeconds(30)) >= 0;
    }
}
