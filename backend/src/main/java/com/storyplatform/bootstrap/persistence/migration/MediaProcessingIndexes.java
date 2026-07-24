package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.media.infrastructure.persistence
        .MongoMediaAssetDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class MediaProcessingIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "ec7819178aed19fb34cc2fb37f6a27bb06f9456ce393e55e7eabdc931653a1ab";

    @Override
    public long version() {
        return 19;
    }

    @Override
    public String name() {
        return "index media processing queue";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoMediaAssetDocument.COLLECTION)
                .createIndex(new Index()
                        .named("media_processing_claim")
                        .on("state", Sort.Direction.ASC)
                        .on("nextAttemptAt", Sort.Direction.ASC)
                        .on("leaseUntil", Sort.Direction.ASC)
                        .on("processingAttempts", Sort.Direction.ASC)
                        .on("receivedAt", Sort.Direction.ASC)
                        .on("_id", Sort.Direction.ASC));
    }
}
