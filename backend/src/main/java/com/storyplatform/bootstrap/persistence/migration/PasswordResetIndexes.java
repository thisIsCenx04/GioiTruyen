package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.identity.infrastructure.persistence
        .MongoPasswordResetDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import java.time.Duration;

public final class PasswordResetIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "5cab616dd78fe840888933ca83b14d88fc647d01e26da5b133de0e7309528f51";

    @Override
    public long version() {
        return 6;
    }

    @Override
    public String name() {
        return "create password reset token indexes";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(MongoPasswordResetDocument.COLLECTION)
                .createIndex(new Index()
                        .named("password_reset_token_hash_unique")
                        .on("tokenHash", Sort.Direction.ASC)
                        .unique());
        mongoTemplate.indexOps(MongoPasswordResetDocument.COLLECTION)
                .createIndex(new Index()
                        .named("password_reset_expiry_ttl")
                        .on("expiresAt", Sort.Direction.ASC)
                        .expire(Duration.ZERO));
        mongoTemplate.indexOps(MongoPasswordResetDocument.COLLECTION)
                .createIndex(new Index()
                        .named("password_reset_user_created")
                        .on("userId", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC));
    }
}
