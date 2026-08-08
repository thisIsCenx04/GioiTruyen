package com.storyplatform.media.application;

import com.storyplatform.media.domain.MediaOwnerType;
import com.storyplatform.media.domain.UploadPurpose;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public interface UploadSignatureOperations {

    UploadGrant issue(String actorId, UploadCommand command);

    record UploadCommand(
            MediaOwnerType ownerType,
            String ownerId,
            UploadPurpose purpose,
            String contentType,
            long sizeBytes,
            String sha256
    ) {
    }

    record UploadGrant(
            String intentId,
            String uploadUrl,
            String apiKey,
            String signature,
            Instant expiresAt,
            long maximumBytes,
            Set<String> allowedContentTypes,
            Map<String, String> parameters
    ) {
    }
}
