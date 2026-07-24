package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.moderation.infrastructure.persistence
        .MongoCopyrightCaseDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class CopyrightCaseIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "8fbda3919948184184dc4044233659006435f9817a9da8e407f93b1958c3a82e";

    @Override
    public long version() {
        return 38;
    }

    @Override
    public String name() {
        return "index copyright takedown cases";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoCopyrightCaseDocument.COLLECTION)
                .createIndex(new Index()
                        .named("copyright_story_unique")
                        .on("storyId", Sort.Direction.ASC)
                        .unique());
        mongo.indexOps(MongoCopyrightCaseDocument.COLLECTION)
                .createIndex(new Index()
                        .named("copyright_sla_queue")
                        .on("status", Sort.Direction.ASC)
                        .on("responseDueAt", Sort.Direction.ASC));
        mongo.indexOps(MongoCopyrightCaseDocument.COLLECTION)
                .createIndex(new Index()
                        .named("copyright_claimant_history")
                        .on("claimantId", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC));
    }
}
