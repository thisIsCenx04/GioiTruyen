package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.monetization.infrastructure.persistence
        .MongoWithdrawalDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoWithdrawalPayoutCallbackDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoWithdrawalPayoutDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class WithdrawalPayoutIndexes implements MongoMigration {

    @Override
    public long version() {
        return 57;
    }

    @Override
    public String name() {
        return "index withdrawal payout claims callbacks and provider refs";
    }

    @Override
    public String checksum() {
        return "f046f02954ab2a8682c2b63147c6639a03c048f9441335783b23386912c28486";
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoWithdrawalDocument.COLLECTION)
                .createIndex(new Index()
                        .named("withdrawal_approved_claim")
                        .on("state", Sort.Direction.ASC)
                        .on("reviewedAt", Sort.Direction.ASC)
                        .on("_id", Sort.Direction.ASC));
        var payouts = mongo.indexOps(
                MongoWithdrawalPayoutDocument.COLLECTION
        );
        payouts.createIndex(new Index()
                .named("withdrawal_payout_retry")
                .on("state", Sort.Direction.ASC)
                .on("nextAttemptAt", Sort.Direction.ASC)
                .on("leaseUntil", Sort.Direction.ASC)
                .on("_id", Sort.Direction.ASC));
        payouts.createIndex(new Index()
                .named("withdrawal_provider_reference_unique")
                .on("provider", Sort.Direction.ASC)
                .on("providerReference", Sort.Direction.ASC)
                .unique()
                .sparse());
        mongo.indexOps(MongoWithdrawalPayoutCallbackDocument.COLLECTION)
                .createIndex(new Index()
                        .named("withdrawal_callback_event_unique")
                        .on("provider", Sort.Direction.ASC)
                        .on("eventId", Sort.Direction.ASC)
                        .unique());
    }
}
