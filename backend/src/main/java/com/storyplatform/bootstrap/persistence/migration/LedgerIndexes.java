package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.monetization.infrastructure.persistence
        .MongoLedgerTransactionDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;

public final class LedgerIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "51cc4f30cd53e396240d0ef8b9d678d8ff1516a018cdf53a451023a48145af58";

    @Override
    public long version() {
        return 46;
    }

    @Override
    public String name() {
        return "index immutable ledger references and history";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoLedgerTransactionDocument.COLLECTION)
                .createIndex(new Index()
                        .named("ledger_reference_unique")
                        .on("referenceType", Sort.Direction.ASC)
                        .on("referenceId", Sort.Direction.ASC)
                        .unique());
        mongo.indexOps(MongoLedgerTransactionDocument.COLLECTION)
                .createIndex(new Index()
                        .named("ledger_idempotency_unique")
                        .on("idempotencyKeyHash", Sort.Direction.ASC)
                        .unique());
        mongo.indexOps(MongoLedgerTransactionDocument.COLLECTION)
                .createIndex(new Index()
                        .named("ledger_compensation_unique")
                        .on("compensatesTransactionId", Sort.Direction.ASC)
                        .unique()
                        .partial(PartialIndexFilter.of(
                                Criteria.where("compensatesTransactionId")
                                        .type(2)
                        )));
        mongo.indexOps(MongoLedgerTransactionDocument.COLLECTION)
                .createIndex(new Index()
                        .named("ledger_account_history")
                        .on("entries.accountId", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC));
    }
}
