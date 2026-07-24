package com.storyplatform.publishing.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record RescheduleStoryRequest(
        @NotNull OffsetDateTime publishAt,
        @NotBlank @Size(max = 64) String timeZone
) {
}
