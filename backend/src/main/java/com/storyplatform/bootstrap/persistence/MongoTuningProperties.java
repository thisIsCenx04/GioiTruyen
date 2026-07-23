package com.storyplatform.bootstrap.persistence;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("app.mongodb")
public record MongoTuningProperties(
        @Min(0) @Max(100) int minPoolSize,
        @Min(1) @Max(500) int maxPoolSize,
        @Min(1) @Max(100) int maxConnecting,
        @NotNull Duration maxWaitTime,
        @NotNull Duration maxIdleTime,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull Duration serverSelectionTimeout,
        @NotNull Duration writeTimeout
) {

    @AssertTrue(message = "minPoolSize must not exceed maxPoolSize")
    public boolean isPoolRangeValid() {
        return minPoolSize <= maxPoolSize;
    }
}
