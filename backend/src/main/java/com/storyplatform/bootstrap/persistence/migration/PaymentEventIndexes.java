package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.monetization.infrastructure.persistence
        .MongoPaymentEventDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class PaymentEventIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "7b8ee147e63ca80850000927ff7cbb1c141814713da9fdc5404aa48a7ccb8700";

    @Override
    public long version() {
        return 50;
    }

    @Override
    public String name() {
        return "deduplicate immutable payment webhook events";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        var indexes = mongo.indexOps(MongoPaymentEventDocument.COLLECTION);
        indexes.createIndex(new Index()
                .named("payment_provider_event_unique")
                .on("provider", Sort.Direction.ASC)
                .on("providerEventId", Sort.Direction.ASC)
                .unique());
        indexes.createIndex(new Index()
                .named("payment_provider_bank_reference_unique")
                .on("provider", Sort.Direction.ASC)
                .on("bankReference", Sort.Direction.ASC)
                .unique());
        indexes.createIndex(new Index()
                .named("payment_status_received")
                .on("status", Sort.Direction.ASC)
                .on("receivedAt", Sort.Direction.ASC));
    }
}
