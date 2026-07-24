package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.reading.infrastructure.persistence
        .MongoReadingProgressRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class ReadingHistoryIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "fd03d087c859653506ce413f3d237919aa04f119f293da4f32f6be858f031767";

    @Override
    public long version() {
        return 31;
    }

    @Override
    public String name() {
        return "index private reading history";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoReadingProgressRepository.COLLECTION)
                .createIndex(new Index()
                        .named("reading_history_user_updated")
                        .on("userId", Sort.Direction.ASC)
                        .on("updatedAt", Sort.Direction.DESC)
                        .on("storyId", Sort.Direction.DESC));
    }
}
