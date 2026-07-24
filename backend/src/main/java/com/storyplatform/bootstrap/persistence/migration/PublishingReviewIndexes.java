package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.publishing.infrastructure.persistence
        .MongoPublishingReviewDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;

public final class PublishingReviewIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "a1d8f80d90eec4731162f42315bda18d9c96d9dac70cf47d275409f12ec8b64e";

    @Override
    public long version() {
        return 22;
    }

    @Override
    public String name() {
        return "index idempotent publishing reviews";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoPublishingReviewDocument.COLLECTION)
                .createIndex(new Index()
                        .named("review_team_idempotency_unique")
                        .on("teamId", Sort.Direction.ASC)
                        .on("idempotencyKey", Sort.Direction.ASC)
                        .unique()
                        .partial(PartialIndexFilter.of(
                                Criteria.where("idempotencyKey").type(2)
                        )));
        mongo.indexOps(MongoPublishingReviewDocument.COLLECTION)
                .createIndex(new Index()
                        .named("review_target_revision_unique")
                        .on("targetType", Sort.Direction.ASC)
                        .on("targetId", Sort.Direction.ASC)
                        .on("submittedRevision", Sort.Direction.ASC)
                        .unique());
        mongo.indexOps(MongoPublishingReviewDocument.COLLECTION)
                .createIndex(new Index()
                        .named("review_state_submitted")
                        .on("targetType", Sort.Direction.ASC)
                        .on("state", Sort.Direction.ASC)
                        .on("submittedAt", Sort.Direction.ASC)
                        .on("_id", Sort.Direction.ASC));
    }
}
