package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.reading.infrastructure.persistence
        .MongoReadingProgressRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class ReadingProgressIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "77744aa561d550047404b9f683c096f020aa9ae029aadeda01f34918ee508d0a";

    @Override
    public long version() {
        return 30;
    }

    @Override
    public String name() {
        return "index private reading progress";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoReadingProgressRepository.COLLECTION)
                .createIndex(new Index()
                        .named("reading_progress_user_story")
                        .on("userId", Sort.Direction.ASC)
                        .on("storyId", Sort.Direction.ASC)
                        .unique());
    }
}
