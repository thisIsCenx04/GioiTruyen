package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.publishing.infrastructure.persistence
        .MongoChapterRevisionDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class ChapterRevisionIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "895931c551cb7590562f76a3de07df74ecf02d660f5db33d87e3be18e8a6e553";

    @Override
    public long version() {
        return 21;
    }

    @Override
    public String name() {
        return "index immutable chapter revisions";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoChapterRevisionDocument.COLLECTION)
                .createIndex(new Index()
                        .named("chapter_revision_number_unique")
                        .on("chapterId", Sort.Direction.ASC)
                        .on("revisionNo", Sort.Direction.ASC)
                        .unique());
        mongo.indexOps(MongoChapterRevisionDocument.COLLECTION)
                .createIndex(new Index()
                        .named("chapter_revision_created")
                        .on("chapterId", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC));
    }
}
