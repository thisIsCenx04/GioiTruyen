package com.storyplatform.bootstrap.persistence.migration;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import java.time.Duration;

public final class MigrationMetadataIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "9e2861eab59d14ccae17c26022e434c8c83bf46c2be9b7bd9da5f55acdc5e827";

    @Override
    public long version() {
        return 1;
    }

    @Override
    public String name() {
        return "create migration metadata indexes";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(MongoMigrationStore.LOCKS_COLLECTION)
                .createIndex(new Index()
                        .named("migration_lock_expiry_ttl")
                        .on("expiresAt", Sort.Direction.ASC)
                        .expire(Duration.ZERO));
    }
}
