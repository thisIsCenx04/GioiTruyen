package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.monetization.infrastructure.persistence
        .MongoRewardSettlementDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoRewardAdjustmentDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class RewardIndexes implements MongoMigration {

    @Override
    public long version() {
        return 53;
    }

    @Override
    public String name() {
        return "index reward period settlement and team history";
    }

    @Override
    public String checksum() {
        return "af92a25ccf293c2938943662a4229938c182a6a0df061ab9aaf9439858da32b2";
    }

    @Override
    public void apply(MongoTemplate mongo) {
        var indexes = mongo.indexOps(
                MongoRewardSettlementDocument.COLLECTION
        );
        indexes.createIndex(new Index()
                .named("reward_period_team_unique")
                .on("periodId", Sort.Direction.ASC)
                .on("teamId", Sort.Direction.ASC)
                .unique());
        indexes.createIndex(new Index()
                .named("reward_team_history")
                .on("teamId", Sort.Direction.ASC)
                .on("periodId", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC));
        indexes.createIndex(new Index()
                .named("reward_period_pending")
                .on("periodId", Sort.Direction.ASC)
                .on("state", Sort.Direction.ASC));
        var adjustments = mongo.indexOps(
                MongoRewardAdjustmentDocument.COLLECTION
        );
        adjustments.createIndex(new Index()
                .named("reward_adjustment_key_unique")
                .on("idempotencyKeyHash", Sort.Direction.ASC)
                .unique());
        adjustments.createIndex(new Index()
                .named("reward_adjustment_settlement_unique")
                .on("settlementId", Sort.Direction.ASC)
                .unique());
    }
}
