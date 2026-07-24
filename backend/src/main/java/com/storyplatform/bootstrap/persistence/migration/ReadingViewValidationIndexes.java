package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.analytics.infrastructure.persistence
        .MongoRawReadingEventRepository;
import com.storyplatform.analytics.infrastructure.persistence
        .MongoReadingViewValidationRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import java.time.Duration;

public final class ReadingViewValidationIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "953e25090f2ab301f5da1564544e13414fe04a86a70e09b4a8dce4a4b2432165";

    @Override
    public long version() {
        return 42;
    }

    @Override
    public String name() {
        return "index versioned reading view validation";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoRawReadingEventRepository.COLLECTION)
                .createIndex(new Index()
                        .named("raw_reading_validation_claim")
                        .on("bucketStart", Sort.Direction.ASC)
                        .on("nextValidationAt", Sort.Direction.ASC)
                        .on("validationLeaseUntil", Sort.Direction.ASC));
        mongo.indexOps(
                MongoReadingViewValidationRepository
                        .FINGERPRINT_COLLECTION
        ).createIndex(new Index()
                .named("reading_view_fingerprint_ttl")
                .on("expiresAt", Sort.Direction.ASC)
                .expire(Duration.ZERO));
        mongo.indexOps(
                MongoReadingViewValidationRepository
                        .CLASSIFICATION_COLLECTION
        ).createIndex(new Index()
                .named("reading_view_rule_valid_time")
                .on("ruleVersion", Sort.Direction.ASC)
                .on("valid", Sort.Direction.ASC)
                .on("classifiedAt", Sort.Direction.ASC));
    }
}
