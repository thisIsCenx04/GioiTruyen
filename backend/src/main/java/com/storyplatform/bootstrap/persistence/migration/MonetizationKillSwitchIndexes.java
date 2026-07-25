package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.monetization.infrastructure.persistence
        .MongoMonetizationKillSwitchAuditDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoPaymentEventDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class MonetizationKillSwitchIndexes
        implements MongoMigration {

    @Override
    public long version() {
        return 59;
    }

    @Override
    public String name() {
        return "index immutable monetization kill switch audits";
    }

    @Override
    public String checksum() {
        return "4e743bb9603c59ee2983eb646604c52b768bfe385963f1490ba0965b75052735";
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoMonetizationKillSwitchAuditDocument.COLLECTION)
                .createIndex(new Index()
                        .named("kill_switch_audit_operation_time")
                        .on("operation", Sort.Direction.ASC)
                        .on("changedAt", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.ASC));
        mongo.indexOps(MongoPaymentEventDocument.COLLECTION)
                .createIndex(new Index()
                        .named("payment_provider_recovery_queue")
                        .on("provider", Sort.Direction.ASC)
                        .on("status", Sort.Direction.ASC)
                        .on("receivedAt", Sort.Direction.ASC)
                        .on("_id", Sort.Direction.ASC));
    }
}
