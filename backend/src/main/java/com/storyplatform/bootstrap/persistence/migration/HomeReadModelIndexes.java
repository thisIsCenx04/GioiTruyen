package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.discovery.infrastructure.persistence
        .MongoHomeReadModelRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class HomeReadModelIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "ddbf8f55d67ca08c2417e60380744180fbfb55d98494c1a40f398ce9d61b3799";

    @Override
    public long version() {
        return 16;
    }

    @Override
    public String name() {
        return "index versioned home read models";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoHomeReadModelRepository.COLLECTION)
                .createIndex(new Index()
                        .named("home_model_generated")
                        .on("generatedAt", Sort.Direction.DESC));
    }
}
