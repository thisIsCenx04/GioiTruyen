package com.storyplatform.media.api;

import com.storyplatform.media.domain.MediaOwnerType;
import com.storyplatform.media.domain.UploadPurpose;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record UploadSignatureRequest(
        @NotNull
        MediaOwnerType ownerType,
        @NotBlank
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
        )
        String ownerId,
        @NotNull
        UploadPurpose purpose,
        @NotBlank
        @Pattern(regexp = "^image/(jpeg|png|webp)$")
        String contentType,
        @Positive
        @Max(15L * 1024L * 1024L)
        long sizeBytes,
        @NotBlank
        @Pattern(regexp = "^[a-fA-F0-9]{64}$")
        String sha256
) {
}
