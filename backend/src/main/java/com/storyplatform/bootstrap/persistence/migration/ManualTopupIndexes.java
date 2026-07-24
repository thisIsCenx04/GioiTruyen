package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.monetization.infrastructure.persistence
        .MongoManualTopupAuditDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoPaymentEventDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class ManualTopupIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "6a62790591d7fb52271ad33d3365636986bc3fabd96316e373197f32766429a9";

    @Override
    public long version() {
        return 51;
    }

    @Override
    public String name() {
        return "index pending payment reviews and manual approval audits";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoPaymentEventDocument.COLLECTION)
                .createIndex(new Index()
                        .named("payment_topup_review")
                        .on("topupRequestId", Sort.Direction.ASC)
                        .on("status", Sort.Direction.ASC));
        mongo.indexOps(MongoManualTopupAuditDocument.COLLECTION)
                .createIndex(new Index()
                        .named("manual_topup_audit_history")
                        .on("topupRequestId", Sort.Direction.ASC)
                        .on("decidedAt", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC));
    }
}
