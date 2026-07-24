package com.storyplatform.publishing.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateChapterDraftRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 2_000_000) String contentHtml
) {
}
