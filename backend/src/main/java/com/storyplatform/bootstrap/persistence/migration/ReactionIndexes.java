package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.community.infrastructure.ReactionCounterStore;
import com.storyplatform.community.infrastructure.persistence
        .MongoReactionRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class ReactionIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "454433adaf4847c28054fd57d44b853d59ddd262bc41cb6f1c4ca849d14f5b4a";

    @Override
    public long version() {
        return 35;
    }

    @Override
    public String name() {
        return "index idempotent community reactions";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoReactionRepository.COLLECTION)
                .createIndex(new Index()
                        .named("reaction_target")
                        .on("targetType", Sort.Direction.ASC)
                        .on("targetId", Sort.Direction.ASC));
        mongo.indexOps(ReactionCounterStore.COLLECTION)
                .createIndex(new Index()
                        .named("reaction_counter_target")
                        .on("targetType", Sort.Direction.ASC)
                        .on("targetId", Sort.Direction.ASC)
                        .unique());
    }
}
