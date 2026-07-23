package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.identity.infrastructure.persistence.MongoUserAccountDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class UserIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "b9c6a9e36a791bae2da9134d874f174f01ef6fef6681a7db99a99e8684c5ffeb";

    @Override
    public long version() {
        return 3;
    }

    @Override
    public String name() {
        return "create unique normalized user email index";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(MongoUserAccountDocument.COLLECTION)
                .createIndex(new Index()
                        .named("user_email_normalized_unique")
                        .on("emailNormalized", Sort.Direction.ASC)
                        .unique());
    }
}
