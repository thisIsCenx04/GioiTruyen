package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.moderation.infrastructure.persistence
        .MongoModerationAppealDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class ModerationAppealIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "f88ff7aa6a4876315ac33cd642607143d745386f9884695831065095cd2e24c7";

    @Override
    public long version() {
        return 37;
    }

    @Override
    public String name() {
        return "index moderation appeal workflow";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoModerationAppealDocument.COLLECTION)
                .createIndex(new Index()
                        .named("appeal_review_unique")
                        .on("reviewId", Sort.Direction.ASC)
                        .unique());
        mongo.indexOps(MongoModerationAppealDocument.COLLECTION)
                .createIndex(new Index()
                        .named("appeal_queue")
                        .on("status", Sort.Direction.ASC)
                        .on("deadline", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.ASC));
        mongo.indexOps(MongoModerationAppealDocument.COLLECTION)
                .createIndex(new Index()
                        .named("appeal_appellant_history")
                        .on("appellantId", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC));
    }
}
