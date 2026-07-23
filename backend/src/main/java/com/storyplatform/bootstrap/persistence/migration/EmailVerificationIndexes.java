package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.identity.infrastructure.persistence
        .MongoEmailVerificationDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import java.time.Duration;

public final class EmailVerificationIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "3455a6d10fbb9ca764f30190233c669ab541b21d9e35f8cb9758ca5318191b03";

    @Override
    public long version() {
        return 4;
    }

    @Override
    public String name() {
        return "create email verification token indexes";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(MongoEmailVerificationDocument.COLLECTION)
                .createIndex(new Index()
                        .named("email_verification_token_hash_unique")
                        .on("tokenHash", Sort.Direction.ASC)
                        .unique());
        mongoTemplate.indexOps(MongoEmailVerificationDocument.COLLECTION)
                .createIndex(new Index()
                        .named("email_verification_expiry_ttl")
                        .on("expiresAt", Sort.Direction.ASC)
                        .expire(Duration.ZERO));
        mongoTemplate.indexOps(MongoEmailVerificationDocument.COLLECTION)
                .createIndex(new Index()
                        .named("email_verification_user_created")
                        .on("userId", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC));
    }
}
