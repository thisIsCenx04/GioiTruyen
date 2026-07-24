package com.storyplatform.media.infrastructure.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoMediaAssetDocument.COLLECTION)
public record MongoMediaAssetDocument(
        @Id String assetId,
        String publicId,
        String intentId,
        String ownerType,
        String ownerId,
        String purpose,
        String resourceType,
        String deliveryType,
        String format,
        long bytes,
        int width,
        int height,
        String state,
        Instant receivedAt,
        long version
) {
    public static final String COLLECTION = "media_assets";
}
