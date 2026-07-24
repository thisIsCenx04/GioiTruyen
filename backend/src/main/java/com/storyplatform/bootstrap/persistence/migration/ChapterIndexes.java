package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.catalog.infrastructure.persistence
        .MongoChapterDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class ChapterIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "906c857ab04d709293f92c67f5d214c308670631476b55e15858307337feadbe";

    @Override
    public long version() {
        return 15;
    }

    @Override
    public String name() {
        return "enforce chapter number and public keyset indexes";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoChapterDocument.COLLECTION)
                .createIndex(new Index()
                        .named("chapter_story_number_unique")
                        .on("storyId", Sort.Direction.ASC)
                        .on("number", Sort.Direction.ASC)
                        .unique());
        mongo.indexOps(MongoChapterDocument.COLLECTION)
                .createIndex(new Index()
                        .named("chapter_public_story_number")
                        .on("storyId", Sort.Direction.ASC)
                        .on("workflowStatus", Sort.Direction.ASC)
                        .on("number", Sort.Direction.ASC)
                        .on("_id", Sort.Direction.ASC));
    }
}
