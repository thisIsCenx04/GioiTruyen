package com.storyplatform.reading.api;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record ReadingCompletionRequest(
        @NotBlank String completionId,
        long finalSequence,
        Instant occurredAt,
        double position
) {
}
