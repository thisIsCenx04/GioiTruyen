package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.publishing.infrastructure
        .PublishingPropagationHandler;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class PublishingPropagationIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "3de2799bf13941af33828947a787710ab95404151d711f8bf9dbe038410de69f";

    @Override
    public long version() {
        return 29;
    }

    @Override
    public String name() {
        return "index publishing propagation tasks";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(PublishingPropagationHandler.COLLECTION)
                .createIndex(new Index()
                        .named("propagation_channel_due")
                        .on("channel", Sort.Direction.ASC)
                        .on("state", Sort.Direction.ASC)
                        .on("availableAt", Sort.Direction.ASC)
                        .on("_id", Sort.Direction.ASC));
    }
}
