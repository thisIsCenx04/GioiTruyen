package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.publishing.infrastructure.persistence
        .MongoPublishingReviewDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class PublishingPrecheckIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "f39ea363de6f2e24be1c69781e2d02f98bf90c11d91d33019ba5bb17649b2a4a";

    @Override
    public long version() {
        return 23;
    }

    @Override
    public String name() {
        return "index publishing precheck leases";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoPublishingReviewDocument.COLLECTION)
                .createIndex(new Index()
                        .named("review_precheck_claim")
                        .on("state", Sort.Direction.ASC)
                        .on("leaseUntil", Sort.Direction.ASC)
                        .on("submittedAt", Sort.Direction.ASC)
                        .on("_id", Sort.Direction.ASC));
    }
}
