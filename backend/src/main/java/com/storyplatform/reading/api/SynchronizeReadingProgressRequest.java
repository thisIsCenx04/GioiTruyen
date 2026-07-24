package com.storyplatform.reading.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record SynchronizeReadingProgressRequest(
        @NotNull String chapterId,
        @DecimalMin("0.0") @DecimalMax("100.0") double position,
        @NotNull Instant deviceUpdatedAt
) {
}
