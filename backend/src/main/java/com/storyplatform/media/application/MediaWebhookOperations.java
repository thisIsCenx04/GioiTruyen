package com.storyplatform.media.application;

import com.storyplatform.media.domain.MediaOwnerType;
import com.storyplatform.media.domain.UploadPurpose;

import java.time.Instant;

public interface MediaWebhookOperations {

    Result accept(byte[] body, String timestamp, String signature);

    enum Result {
        ACCEPTED,
        DUPLICATE
    }

    record AssetEvent(
            String eventId,
            String assetId,
            String publicId,
            String intentId,
            MediaOwnerType ownerType,
            String ownerId,
            UploadPurpose purpose,
            String resourceType,
            String deliveryType,
            String format,
            long cloudinaryVersion,
            String declaredSha256,
            long bytes,
            int width,
            int height,
            Instant receivedAt
    ) {
    }
}
