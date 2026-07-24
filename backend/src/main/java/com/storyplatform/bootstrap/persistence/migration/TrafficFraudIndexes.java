package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.analytics.infrastructure.persistence
        .MongoReadingViewValidationRepository;
import com.storyplatform.analytics.infrastructure.persistence
        .MongoTrafficFraudRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class TrafficFraudIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "125413e3895b834a28729e740bb09d739d2648651634f5834ef5e4c51f0c811d";

    @Override
    public long version() {
        return 43;
    }

    @Override
    public String name() {
        return "index explainable traffic fraud review";
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
                .named("traffic_fraud_claim")
                .on("fraudRuleVersion", Sort.Direction.ASC)
                .on("fraudRetryAt", Sort.Direction.ASC)
                .on("fraudLeaseUntil", Sort.Direction.ASC)
                .on("classifiedAt", Sort.Direction.ASC));
        mongo.indexOps(MongoTrafficFraudRepository.CASE_COLLECTION)
                .createIndex(new Index()
                        .named("traffic_review_state_score")
                        .on("state", Sort.Direction.ASC)
                        .on("maximumScore", Sort.Direction.DESC)
                        .on("updatedAt", Sort.Direction.ASC));
    }
}
