package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.monetization.infrastructure.persistence
        .MongoDonationDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class DonationIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "19e6d4eef9bc495f0cdf969c94253614ad4157c544535c863856ed320cb47e3d";

    @Override
    public long version() {
        return 52;
    }

    @Override
    public String name() {
        return "index idempotent donation and account histories";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        var indexes = mongo.indexOps(MongoDonationDocument.COLLECTION);
        indexes.createIndex(new Index()
                .named("donation_idempotency_unique")
                .on("idempotencyKeyHash", Sort.Direction.ASC)
                .unique());
        indexes.createIndex(new Index()
                .named("donation_ledger_transaction_unique")
                .on("ledgerTransactionId", Sort.Direction.ASC)
                .unique());
        indexes.createIndex(new Index()
                .named("donation_donor_history")
                .on("donorAccountId", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC));
        indexes.createIndex(new Index()
                .named("donation_team_history")
                .on("teamAccountId", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC));
    }
}
