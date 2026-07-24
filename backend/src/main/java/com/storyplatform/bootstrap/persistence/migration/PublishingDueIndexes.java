package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.publishing.infrastructure.persistence
        .MongoPublishingScheduleDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class PublishingDueIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "3d2dd617b9c35062c273800bc6fb54a9609f00b140fdddc0704ae7e8e6f48e0b";

    @Override
    public long version() {
        return 28;
    }

    @Override
    public String name() {
        return "index due publishing leases";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoPublishingScheduleDocument.COLLECTION)
                .createIndex(new Index()
                        .named("schedule_due_lease_claim")
                        .on("state", Sort.Direction.ASC)
                        .on("publishAt", Sort.Direction.ASC)
                        .on("leaseUntil", Sort.Direction.ASC)
                        .on("_id", Sort.Direction.ASC));
    }
}
