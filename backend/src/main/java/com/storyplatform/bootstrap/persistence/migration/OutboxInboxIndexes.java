package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.shared.events.persistence.InboxReceipt;
import com.storyplatform.shared.events.persistence.OutboxMessage;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class OutboxInboxIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "faee033456576009a5a87abaa2a5a1e374c3538a593c0c149e31b10c7606cc18";

    @Override
    public long version() {
        return 2;
    }

    @Override
    public String name() {
        return "create outbox and inbox indexes";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(OutboxMessage.COLLECTION)
                .createIndex(new Index()
                        .named("outbox_claim_v1")
                        .on("status", Sort.Direction.ASC)
                        .on("nextAttemptAt", Sort.Direction.ASC)
                        .on("leaseUntil", Sort.Direction.ASC)
                        .on("_id", Sort.Direction.ASC));
        mongoTemplate.indexOps(InboxReceipt.COLLECTION)
                .createIndex(new Index()
                        .named("inbox_consumer_event_unique")
                        .on("consumer", Sort.Direction.ASC)
                        .on("eventId", Sort.Direction.ASC)
                        .unique());
    }
}
