package com.storyplatform.publishing.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record ScheduleStoryRequest(
        @NotNull OffsetDateTime publishAt,
        @NotBlank @Size(max = 64) String timeZone,
        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$")
        String revision
) {
}
