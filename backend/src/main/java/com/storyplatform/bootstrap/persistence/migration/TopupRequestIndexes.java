package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.monetization.infrastructure.persistence
        .MongoTopupRequestDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class TopupRequestIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "144b18816bb6b05270f7a0f47f0be97e2995275a455505132d65401d9c42ff89";

    @Override
    public long version() {
        return 49;
    }

    @Override
    public String name() {
        return "index private idempotent topup requests";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        var indexes = mongo.indexOps(MongoTopupRequestDocument.COLLECTION);
        indexes.createIndex(new Index()
                .named("topup_transfer_reference_unique")
                .on("transferReference", Sort.Direction.ASC)
                .unique());
        indexes.createIndex(new Index()
                .named("topup_idempotency_key_hash_unique")
                .on("idempotencyKeyHash", Sort.Direction.ASC)
                .unique());
        indexes.createIndex(new Index()
                .named("topup_user_created_id")
                .on("userId", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC));
        indexes.createIndex(new Index()
                .named("topup_state_expiry")
                .on("status", Sort.Direction.ASC)
                .on("expiresAt", Sort.Direction.ASC));
    }
}
