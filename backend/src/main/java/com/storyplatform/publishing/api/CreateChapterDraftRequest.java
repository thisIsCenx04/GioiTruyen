package com.storyplatform.publishing.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateChapterDraftRequest(
        @Min(1) @Max(100_000) int number,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 1_000_000) String contentHtml
) {
}
