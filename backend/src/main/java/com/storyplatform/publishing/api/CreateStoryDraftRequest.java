package com.storyplatform.publishing.api;

import com.storyplatform.publishing.domain.StoryDraft;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateStoryDraftRequest(
        @NotBlank
        @Size(max = 200)
        String title,
        @NotBlank
        @Size(max = 5000)
        String synopsis,
        @NotNull
        StoryDraft.Origin origin,
        @NotBlank
        @Size(max = 16)
        @Pattern(regexp = "[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})*")
        String language,
        @NotEmpty
        @Size(max = 30)
        List<
                @Pattern(
                        regexp = "^[0-9a-fA-F-]{36}$"
                ) String> categoryIds,
        @Pattern(
                regexp = "^[0-9a-fA-F-]{36}$"
        )
        String coverAssetId
) {
}
