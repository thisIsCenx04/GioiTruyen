package com.storyplatform.media.infrastructure.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoWebhookReceipt.COLLECTION)
public record MongoWebhookReceipt(
        @Id String id,
        Instant receivedAt
) {
    public static final String COLLECTION = "media_webhook_receipts";
}
