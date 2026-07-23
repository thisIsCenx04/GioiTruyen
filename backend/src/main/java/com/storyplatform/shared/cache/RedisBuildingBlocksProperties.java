package com.storyplatform.shared.cache;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("app.redis")
public record RedisBuildingBlocksProperties(
        @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9._~-]{0,31}")
        String applicationPrefix,
        @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9._~-]{0,31}")
        String keyVersion,
        @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9._~-]{0,31}")
        String environment,
        @NotNull Duration minimumCacheTtl,
        @NotNull Duration maximumCacheTtl,
        @DecimalMin("0.0") @DecimalMax("0.5") double ttlJitterRatio
) {

    @AssertTrue(message = "Redis cache TTL range must be positive and ordered")
    public boolean isCacheTtlRangeValid() {
        if (minimumCacheTtl == null || maximumCacheTtl == null) {
            return true;
        }
        return minimumCacheTtl.isPositive()
                && !minimumCacheTtl.minus(maximumCacheTtl).isPositive();
    }
}
