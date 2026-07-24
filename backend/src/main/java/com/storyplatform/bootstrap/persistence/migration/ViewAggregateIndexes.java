package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.analytics.infrastructure.persistence
        .MongoReadingViewValidationRepository;
import com.storyplatform.analytics.infrastructure.persistence
        .MongoViewAggregateRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class ViewAggregateIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "037f21fb6a8ce49ab30917ce29929b105230718569cff716086d352d1cddab9e";

    @Override
    public long version() {
        return 44;
    }

    @Override
    public String name() {
        return "index idempotent hourly and daily view aggregates";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(
                MongoReadingViewValidationRepository
                        .CLASSIFICATION_COLLECTION
        ).createIndex(new Index()
                .named("view_aggregate_claim")
                .on("fraudRuleVersion", Sort.Direction.ASC)
                .on("aggregateVersion", Sort.Direction.ASC)
                .on("aggregateRetryAt", Sort.Direction.ASC)
                .on("aggregateLeaseUntil", Sort.Direction.ASC)
                .on("occurredAt", Sort.Direction.ASC));
        mongo.indexOps(MongoViewAggregateRepository.COLLECTION)
                .createIndex(new Index()
                        .named("view_aggregate_story_period")
                        .on("storyId", Sort.Direction.ASC)
                        .on("period", Sort.Direction.ASC)
                        .on("bucketStart", Sort.Direction.ASC));
    }
}
