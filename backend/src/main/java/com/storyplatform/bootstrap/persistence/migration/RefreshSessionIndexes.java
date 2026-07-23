package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.identity.infrastructure.persistence
        .MongoRefreshTokenFamilyDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import java.time.Duration;

public final class RefreshSessionIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "86c423fa8890e72f1189d97bc098ec633deefbcad52360c76289d9ff3067b6b7";

    @Override
    public long version() {
        return 5;
    }

    @Override
    public String name() {
        return "create refresh token family indexes";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(MongoRefreshTokenFamilyDocument.COLLECTION)
                .createIndex(new Index()
                        .named("refresh_current_token_hash_unique")
                        .on("currentTokenHash", Sort.Direction.ASC)
                        .unique());
        mongoTemplate.indexOps(MongoRefreshTokenFamilyDocument.COLLECTION)
                .createIndex(new Index()
                        .named("refresh_used_token_hash")
                        .on("usedTokenHashes", Sort.Direction.ASC));
        mongoTemplate.indexOps(MongoRefreshTokenFamilyDocument.COLLECTION)
                .createIndex(new Index()
                        .named("refresh_user_updated")
                        .on("userId", Sort.Direction.ASC)
                        .on("updatedAt", Sort.Direction.DESC));
        mongoTemplate.indexOps(MongoRefreshTokenFamilyDocument.COLLECTION)
                .createIndex(new Index()
                        .named("refresh_expiry_ttl")
                        .on("expiresAt", Sort.Direction.ASC)
                        .expire(Duration.ZERO));
    }
}
