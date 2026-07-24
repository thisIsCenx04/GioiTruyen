package com.storyplatform.bootstrap.persistence.migration;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;

public final class StoryTextSearchIndex implements MongoMigration {

    private static final String CHECKSUM =
            "71365ccb0a027ba3410fbf3e6a73fc326177df3035462914944618048efe2f5b";

    @Override
    public long version() {
        return 17;
    }

    @Override
    public String name() {
        return "create bounded story text search fallback";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        TextIndexDefinition index = new TextIndexDefinition
                .TextIndexDefinitionBuilder()
                .named("story_text_fallback")
                .onField("title", 10F)
                .onField("aliases", 7F)
                .onField("synopsis", 1F)
                .build();
        mongo.indexOps("stories").createIndex(index);
    }
}
