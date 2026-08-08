package com.storyplatform.identity.infrastructure;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.identity.registration")
public record RegistrationProperties(
        @NotBlank
        @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}")
        String currentConsentVersion,
        @Min(19_456)
        @Max(1_048_576)
        int argon2MemoryKb,
        @Min(2)
        @Max(20)
        int argon2Iterations,
        @Min(1)
        @Max(16)
        int argon2Parallelism
) {
}
