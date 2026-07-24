package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.analytics.infrastructure.persistence
        .MongoViewAggregateRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class TeamAnalyticsIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "a9db3388e37a44f11d20925c62e04edfc612dd055501372190f666871098e515";

    @Override
    public long version() {
        return 45;
    }

    @Override
    public String name() {
        return "index bounded aggregate analytics queries";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoViewAggregateRepository.COLLECTION)
                .createIndex(new Index()
                        .named("view_aggregate_period_bucket_story")
                        .on("period", Sort.Direction.ASC)
                        .on("bucketStart", Sort.Direction.ASC)
                        .on("storyId", Sort.Direction.ASC));
    }
}
