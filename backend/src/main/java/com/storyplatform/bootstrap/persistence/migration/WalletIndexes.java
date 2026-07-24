package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.monetization.infrastructure.persistence
        .MongoWalletAccountDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class WalletIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "d891f6a74720fe224bb0b41076da4566e6b38ea545ea61fffb5b43387aa0861d";

    @Override
    public long version() {
        return 47;
    }

    @Override
    public String name() {
        return "index unique xu wallet owners";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoWalletAccountDocument.COLLECTION)
                .createIndex(new Index()
                        .named("wallet_owner_currency_unique")
                        .on("ownerType", Sort.Direction.ASC)
                        .on("ownerId", Sort.Direction.ASC)
                        .on("currency", Sort.Direction.ASC)
                        .unique());
    }
}
