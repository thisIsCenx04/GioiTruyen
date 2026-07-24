package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.monetization.infrastructure.persistence
        .MongoWithdrawalDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class WithdrawalReviewIndexes implements MongoMigration {

    @Override
    public long version() {
        return 56;
    }

    @Override
    public String name() {
        return "index idempotent withdrawal review decisions";
    }

    @Override
    public String checksum() {
        return "e77e40464598954c758b86843d88dba890e044f2b0d3e51cc8d0cfbe6eac38e7";
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoWithdrawalDocument.COLLECTION)
                .createIndex(new Index()
                        .named("withdrawal_review_key_unique")
                        .on("reviewKeyHash", Sort.Direction.ASC)
                        .unique()
                        .sparse());
    }
}
