package com.storyplatform.media.infrastructure.persistence;

import com.storyplatform.media.application.MediaWebhookOperations;
import com.storyplatform.media.application.port.MediaAssetRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Objects;

public final class MongoMediaAssetRepository
        implements MediaAssetRepository {

    private final MongoTemplate mongo;

    public MongoMediaAssetRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public boolean recordPending(
            MediaWebhookOperations.AssetEvent event
    ) {
        MongoWebhookReceipt receipt = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(event.eventId())),
                new Update()
                        .setOnInsert("_id", event.eventId())
                        .setOnInsert("receivedAt", event.receivedAt()),
                FindAndModifyOptions.options()
                        .upsert(true)
                        .returnNew(false),
                MongoWebhookReceipt.class
        );
        if (receipt != null) {
            return false;
        }
        MongoMediaAssetDocument existing = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(event.assetId())),
                assetInsert(event),
                FindAndModifyOptions.options()
                        .upsert(true)
                        .returnNew(false),
                MongoMediaAssetDocument.class
        );
        return existing == null;
    }

    private static Update assetInsert(
            MediaWebhookOperations.AssetEvent event
    ) {
        return new Update()
                .setOnInsert("_id", event.assetId())
                .setOnInsert("publicId", event.publicId())
                .setOnInsert("intentId", event.intentId())
                .setOnInsert("ownerType", event.ownerType().name())
                .setOnInsert("ownerId", event.ownerId())
                .setOnInsert("purpose", event.purpose().name())
                .setOnInsert("resourceType", event.resourceType())
                .setOnInsert("deliveryType", event.deliveryType())
                .setOnInsert("format", event.format())
                .setOnInsert(
                        "cloudinaryVersion",
                        event.cloudinaryVersion()
                )
                .setOnInsert(
                        "declaredSha256",
                        event.declaredSha256()
                )
                .setOnInsert("bytes", event.bytes())
                .setOnInsert("width", event.width())
                .setOnInsert("height", event.height())
                .setOnInsert("state", "PENDING_MODERATION")
                .setOnInsert("receivedAt", event.receivedAt())
                .setOnInsert("version", 0L)
                .setOnInsert("processingAttempts", 0);
    }
}
