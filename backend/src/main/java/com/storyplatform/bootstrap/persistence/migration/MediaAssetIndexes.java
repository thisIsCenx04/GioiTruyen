package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.media.infrastructure.persistence
        .MongoMediaAssetDocument;
import com.storyplatform.media.infrastructure.persistence
        .MongoWebhookReceipt;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import java.time.Duration;

public final class MediaAssetIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "fc7502527c7d92778bb44e5a58fed9b33fc36a53b25e7fc8c01f40b2849db241";

    @Override
    public long version() {
        return 18;
    }

    @Override
    public String name() {
        return "index media assets and webhook receipts";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoMediaAssetDocument.COLLECTION)
                .createIndex(new Index()
                        .named("media_intent_unique")
                        .on("intentId", Sort.Direction.ASC)
                        .unique());
        mongo.indexOps(MongoMediaAssetDocument.COLLECTION)
                .createIndex(new Index()
                        .named("media_moderation_queue")
                        .on("state", Sort.Direction.ASC)
                        .on("receivedAt", Sort.Direction.ASC));
        mongo.indexOps(MongoMediaAssetDocument.COLLECTION)
                .createIndex(new Index()
                        .named("media_owner_purpose")
                        .on("ownerType", Sort.Direction.ASC)
                        .on("ownerId", Sort.Direction.ASC)
                        .on("purpose", Sort.Direction.ASC));
        mongo.indexOps(MongoWebhookReceipt.COLLECTION)
                .createIndex(new Index()
                        .named("media_receipt_ttl")
                        .on("receivedAt", Sort.Direction.ASC)
                        .expire(Duration.ofDays(7)));
    }
}
