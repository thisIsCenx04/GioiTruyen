package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.catalog.infrastructure.persistence
        .MongoStoryDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class StoryIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "1d063c73133d9d6730c355033458e9892fdef111348464066d46eff3692337b1";

    @Override
    public long version() {
        return 13;
    }

    @Override
    public String name() {
        return "create story uniqueness and catalog query indexes";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoStoryDocument.COLLECTION)
                .createIndex(new Index()
                        .named("story_slug_unique")
                        .on("slug", Sort.Direction.ASC)
                        .unique());
        mongo.indexOps(MongoStoryDocument.COLLECTION)
                .createIndex(new Index()
                        .named("story_public_latest")
                        .on("workflowStatus", Sort.Direction.ASC)
                        .on("publishedAt", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC));
        mongo.indexOps(MongoStoryDocument.COLLECTION)
                .createIndex(new Index()
                        .named("story_team_status_updated")
                        .on("teamId", Sort.Direction.ASC)
                        .on("workflowStatus", Sort.Direction.ASC)
                        .on("updatedAt", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC));
        mongo.indexOps(MongoStoryDocument.COLLECTION)
                .createIndex(new Index()
                        .named("story_public_taxonomy_updated")
                        .on("workflowStatus", Sort.Direction.ASC)
                        .on("categoryIds", Sort.Direction.ASC)
                        .on("updatedAt", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC));
    }
}
