package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.moderation.infrastructure.persistence
        .MongoModerationAuditDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class ModerationAuditIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "10336aa5859bb65418bbfff628175683041dc4fd6c0ab66a553a335221481073";

    @Override
    public long version() {
        return 25;
    }

    @Override
    public String name() {
        return "index immutable moderation audit trail";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoModerationAuditDocument.COLLECTION)
                .createIndex(new Index()
                        .named("audit_target_created")
                        .on("targetType", Sort.Direction.ASC)
                        .on("targetId", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.ASC));
    }
}
