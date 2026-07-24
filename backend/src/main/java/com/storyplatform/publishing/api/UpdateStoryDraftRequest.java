package com.storyplatform.publishing.api;

import com.storyplatform.publishing.domain.StoryDraft;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateStoryDraftRequest(
        @Size(min = 1, max = 200)
        String title,
        @Size(min = 1, max = 5000)
        String synopsis,
        @Size(min = 1, max = 30)
        List<
                @Pattern(
                        regexp = "^[0-9a-fA-F-]{36}$"
                ) String> categoryIds,
        @Pattern(
                regexp = "^[0-9a-fA-F-]{36}$"
        )
        String coverAssetId,
        StoryDraft.CompletionStatus completionStatus
) {
}
