package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.moderation.infrastructure.persistence
        .MongoModerationReviewDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class ModerationQueueIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "a747975cd4985c5643431bd17a194a15c3fb94ad49655761e2afd17bf9cc62ee";

    @Override
    public long version() {
        return 24;
    }

    @Override
    public String name() {
        return "index prioritized moderation review queue";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoModerationReviewDocument.COLLECTION)
                .createIndex(new Index()
                        .named("review_queue_priority")
                        .on("targetType", Sort.Direction.ASC)
                        .on("state", Sort.Direction.ASC)
                        .on("priority", Sort.Direction.DESC)
                        .on("submittedAt", Sort.Direction.ASC)
                        .on("_id", Sort.Direction.ASC));
    }
}
