package com.storyplatform.reading.api;

import jakarta.validation.constraints.NotBlank;

public record StartReadingSessionRequest(
        @NotBlank String storyId,
        @NotBlank String chapterId,
        String anonymousId
) {
}
