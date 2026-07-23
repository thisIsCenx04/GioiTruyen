package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.identity.infrastructure.persistence
        .MongoReauthenticationGrantDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import java.time.Duration;

public final class ReauthenticationGrantIndexes
        implements MongoMigration {

    private static final String CHECKSUM =
            "ef043aa8ae8bcde5af4610690d1af1690aa6035640dd902cff4315118ab86dc3";

    @Override
    public long version() {
        return 8;
    }

    @Override
    public String name() {
        return "create scoped reauthentication grant indexes";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(
                MongoReauthenticationGrantDocument.COLLECTION
        ).createIndex(new Index()
                .named("reauth_token_hash_unique")
                .on("tokenHash", Sort.Direction.ASC)
                .unique());
        mongoTemplate.indexOps(
                MongoReauthenticationGrantDocument.COLLECTION
        ).createIndex(new Index()
                .named("reauth_expiry_ttl")
                .on("expiresAt", Sort.Direction.ASC)
                .expire(Duration.ZERO));
        mongoTemplate.indexOps(
                MongoReauthenticationGrantDocument.COLLECTION
        ).createIndex(new Index()
                .named("reauth_actor_created")
                .on("actorId", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC));
    }
}
