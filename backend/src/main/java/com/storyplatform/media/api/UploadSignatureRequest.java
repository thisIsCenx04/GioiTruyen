package com.storyplatform.media.api;

import com.storyplatform.media.domain.MediaOwnerType;
import com.storyplatform.media.domain.UploadPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record UploadSignatureRequest(
        @NotNull MediaOwnerType ownerType,
        @NotBlank
        @Pattern(
                regexp = "^[0-9a-fA-F-]{36}$",
                message = "ownerId must be a UUID"
        )
        String ownerId,
        @NotNull UploadPurpose purpose,
        @NotBlank String contentType,
        @Positive long sizeBytes,
        @NotBlank
        @Pattern(
                regexp = "^[0-9a-f]{64}$",
                message = "sha256 must be lowercase hexadecimal"
        )
        String sha256
) {
}
