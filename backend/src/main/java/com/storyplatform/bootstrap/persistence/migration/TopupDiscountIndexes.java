package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.monetization.infrastructure.persistence
        .MongoTopupDiscountDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class TopupDiscountIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "4a9db187c742816a2b52e050eeac67713fa7d2e59001052b9614d602a2b65ac9";

    @Override
    public long version() {
        return 48;
    }

    @Override
    public String name() {
        return "index versioned topup discount configuration";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoTopupDiscountDocument.COLLECTION)
                .createIndex(new Index()
                        .named("monetization_config_key_version_unique")
                        .on("key", Sort.Direction.ASC)
                        .on("version", Sort.Direction.DESC)
                        .unique());
    }
}
