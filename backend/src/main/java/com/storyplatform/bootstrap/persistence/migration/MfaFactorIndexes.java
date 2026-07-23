package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.identity.infrastructure.persistence
        .MongoMfaFactorDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class MfaFactorIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "f5c2c29ef971426f9bd591a4cdfc13531787c08e968084bbcddeaa87b785f017";

    @Override
    public long version() {
        return 7;
    }

    @Override
    public String name() {
        return "create MFA factor indexes";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(MongoMfaFactorDocument.COLLECTION)
                .createIndex(new Index()
                        .named("mfa_enabled_updated")
                        .on("enabled", Sort.Direction.ASC)
                        .on("updatedAt", Sort.Direction.DESC));
    }
}
