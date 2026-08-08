package com.storyplatform.teams.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank
        @Size(min = 2, max = 80)
        String displayName,
        @Size(max = 500)
        String bio,
        @Pattern(
                regexp = "^[0-9a-fA-F-]{36}$",
                message = "AVATAR_MEDIA_ID_INVALID"
        )
        String avatarMediaId,
        @NotNull
        @Min(0)
        Long version
) {
}
