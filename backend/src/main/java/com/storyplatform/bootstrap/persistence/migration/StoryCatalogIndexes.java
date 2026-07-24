package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.catalog.infrastructure.persistence
        .MongoStoryDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class StoryCatalogIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "c966b72fb1f7993cb83d059a86728422179cba48db1cc8ed6c30220e0649f39e";

    @Override
    public long version() {
        return 14;
    }

    @Override
    public String name() {
        return "support public story updated keyset ordering";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoStoryDocument.COLLECTION)
                .createIndex(new Index()
                        .named("story_public_updated")
                        .on("workflowStatus", Sort.Direction.ASC)
                        .on("updatedAt", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC));
    }
}
