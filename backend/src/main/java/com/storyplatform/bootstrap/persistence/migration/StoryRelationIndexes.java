package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.community.infrastructure.StoryRelationCounterStore;
import com.storyplatform.community.infrastructure.persistence
        .MongoStoryRelationRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class StoryRelationIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "57be7492b4970622d1948ad100c9bf1c25b0b64ae6477382b17ba0c9b518cf21";

    @Override
    public long version() {
        return 33;
    }

    @Override
    public String name() {
        return "create unique story relations and counters";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoStoryRelationRepository.COLLECTION)
                .createIndex(new Index()
                        .named("story_relation_story_type")
                        .on("storyId", Sort.Direction.ASC)
                        .on("type", Sort.Direction.ASC));
        mongo.indexOps(StoryRelationCounterStore.COLLECTION)
                .createIndex(new Index()
                        .named("story_relation_counter_story")
                        .on("storyId", Sort.Direction.ASC)
                        .unique());
    }
}
