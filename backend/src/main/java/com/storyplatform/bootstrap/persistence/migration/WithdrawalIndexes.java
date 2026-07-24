package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.monetization.infrastructure.persistence
        .MongoWithdrawalDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class WithdrawalIndexes implements MongoMigration {

    @Override
    public long version() {
        return 55;
    }

    @Override
    public String name() {
        return "index withdrawal idempotency team history and review queue";
    }

    @Override
    public String checksum() {
        return "4280e7a2a82ab9b492157b561c7678723475066f3bcac90bcb118a09e9555b51";
    }

    @Override
    public void apply(MongoTemplate mongo) {
        var indexes = mongo.indexOps(MongoWithdrawalDocument.COLLECTION);
        indexes.createIndex(new Index()
                .named("withdrawal_idempotency_unique")
                .on("idempotencyKeyHash", Sort.Direction.ASC)
                .unique());
        indexes.createIndex(new Index()
                .named("withdrawal_team_history")
                .on("teamId", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC));
        indexes.createIndex(new Index()
                .named("withdrawal_review_queue")
                .on("state", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.ASC)
                .on("_id", Sort.Direction.ASC));
    }
}
