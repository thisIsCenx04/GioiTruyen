package com.storyplatform.publishing.api;

import com.storyplatform.publishing.application.ContentVisibilityOperations;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ContentVisibilityRequest(
        @NotNull ContentVisibilityOperations.Action action,
        @NotBlank
        @Pattern(regexp = "[A-Z][A-Z0-9_]{2,63}")
        String reasonCode,
        @Size(max = 1000) String note
) {
}
