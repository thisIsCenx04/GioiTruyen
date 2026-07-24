package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.publishing.infrastructure.persistence
        .MongoStoryDraftDocument;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoStoryRevisionDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;

public final class StoryDraftIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "0ab781a1fbf0d6890d7d909063364187c4dd19a9f130ef400e48d21b0f840af7";

    @Override
    public long version() {
        return 20;
    }

    @Override
    public String name() {
        return "index story draft idempotency and revisions";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoStoryDraftDocument.COLLECTION)
                .createIndex(new Index()
                        .named("story_team_idempotency_unique")
                        .on("teamId", Sort.Direction.ASC)
                        .on("idempotencyKey", Sort.Direction.ASC)
                        .unique()
                        .partial(PartialIndexFilter.of(
                                Criteria.where("idempotencyKey")
                                        .type(2)
                        )));
        mongo.indexOps(MongoStoryRevisionDocument.COLLECTION)
                .createIndex(new Index()
                        .named("story_revision_number_unique")
                        .on("storyId", Sort.Direction.ASC)
                        .on("revisionNo", Sort.Direction.ASC)
                        .unique());
        mongo.indexOps(MongoStoryRevisionDocument.COLLECTION)
                .createIndex(new Index()
                        .named("story_revision_created")
                        .on("storyId", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC));
    }
}
