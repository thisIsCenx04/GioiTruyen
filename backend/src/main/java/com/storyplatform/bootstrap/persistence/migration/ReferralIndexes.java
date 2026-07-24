package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.monetization.infrastructure.persistence
        .MongoReferralAttributionDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class ReferralIndexes implements MongoMigration {

    @Override
    public long version() {
        return 54;
    }

    @Override
    public String name() {
        return "index one-attribution referral fraud and reward queries";
    }

    @Override
    public String checksum() {
        return "921ac2695781fa1f8cd6a306e1898719348f06d0840bc7a8c504773144005bbb";
    }

    @Override
    public void apply(MongoTemplate mongo) {
        var indexes = mongo.indexOps(
                MongoReferralAttributionDocument.COLLECTION
        );
        indexes.createIndex(new Index()
                .named("referral_referee_unique")
                .on("refereeId", Sort.Direction.ASC)
                .unique());
        indexes.createIndex(new Index()
                .named("referral_idempotency_unique")
                .on("idempotencyKeyHash", Sort.Direction.ASC)
                .unique());
        indexes.createIndex(new Index()
                .named("referral_rewardable")
                .on("state", Sort.Direction.ASC)
                .on("eligibleAt", Sort.Direction.ASC)
                .on("_id", Sort.Direction.ASC));
        indexes.createIndex(new Index()
                .named("referral_referrer_window")
                .on("referrerId", Sort.Direction.ASC)
                .on("attributedAt", Sort.Direction.DESC));
    }
}
