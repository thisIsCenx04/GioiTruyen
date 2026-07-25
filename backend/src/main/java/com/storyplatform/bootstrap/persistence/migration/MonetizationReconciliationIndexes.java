package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.monetization.infrastructure.persistence
        .MongoMonetizationReconciliationCaseDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoMonetizationReconciliationRunDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class MonetizationReconciliationIndexes
        implements MongoMigration {

    @Override
    public long version() {
        return 58;
    }

    @Override
    public String name() {
        return "index reconciliation windows cases and evidence";
    }

    @Override
    public String checksum() {
        return "90d0f92cc636a8ce726f55e2d67062ad97231769c0484eb24e052d43f0caf7d1";
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoMonetizationReconciliationRunDocument.COLLECTION)
                .createIndex(new Index()
                        .named("reconciliation_provider_window_unique")
                        .on("provider", Sort.Direction.ASC)
                        .on("from", Sort.Direction.ASC)
                        .on("to", Sort.Direction.ASC)
                        .unique());
        var cases = mongo.indexOps(
                MongoMonetizationReconciliationCaseDocument.COLLECTION
        );
        cases.createIndex(new Index()
                .named("reconciliation_case_per_run_unique")
                .on("runId", Sort.Direction.ASC)
                .on("subjectType", Sort.Direction.ASC)
                .on("providerReference", Sort.Direction.ASC)
                .on("mismatch", Sort.Direction.ASC)
                .unique());
        cases.createIndex(new Index()
                .named("reconciliation_open_case_queue")
                .on("status", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.ASC)
                .on("_id", Sort.Direction.ASC));
    }
}
