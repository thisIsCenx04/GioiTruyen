package com.storyplatform.shared.events.persistence;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class OutboxMessageStore {

    private final MongoTemplate mongoTemplate;

    public OutboxMessageStore(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<OutboxMessage> claim(
            String owner,
            Instant now,
            Duration leaseDuration
    ) {
        Query claimable = new Query(new Criteria().andOperator(
                Criteria.where("status").in(
                        OutboxStatus.PENDING,
                        OutboxStatus.PROCESSING
                ),
                Criteria.where("nextAttemptAt").lte(now),
                new Criteria().orOperator(
                        Criteria.where("leaseUntil").is(null),
                        Criteria.where("leaseUntil").lte(now)
                )
        )).with(Sort.by(
                Sort.Order.asc("nextAttemptAt"),
                Sort.Order.asc("_id")
        ));
        Update claim = new Update()
                .set("status", OutboxStatus.PROCESSING)
                .set("leaseOwner", owner)
                .set("leaseUntil", now.plus(leaseDuration))
                .inc("attempts", 1);

        return Optional.ofNullable(mongoTemplate.findAndModify(
                claimable,
                claim,
                FindAndModifyOptions.options().returnNew(true),
                OutboxMessage.class
        ));
    }

    public void complete(String id, String owner, Instant processedAt) {
        Update update = new Update()
                .set("status", OutboxStatus.PROCESSED)
                .set("processedAt", processedAt)
                .unset("leaseOwner")
                .unset("leaseUntil")
                .unset("lastError");
        updateOwned(id, owner, update);
    }

    public void scheduleRetry(
            String id,
            String owner,
            Instant nextAttemptAt,
            String errorCode
    ) {
        Update update = new Update()
                .set("status", OutboxStatus.PENDING)
                .set("nextAttemptAt", nextAttemptAt)
                .set("lastError", errorCode)
                .unset("leaseOwner")
                .unset("leaseUntil");
        updateOwned(id, owner, update);
    }

    public void deadLetter(
            String id,
            String owner,
            Instant failedAt,
            String errorCode
    ) {
        Update update = new Update()
                .set("status", OutboxStatus.DEAD_LETTER)
                .set("processedAt", failedAt)
                .set("lastError", errorCode)
                .unset("leaseOwner")
                .unset("leaseUntil");
        updateOwned(id, owner, update);
    }

    private void updateOwned(String id, String owner, Update update) {
        Query owned = Query.query(Criteria.where("_id").is(id)
                .and("status").is(OutboxStatus.PROCESSING)
                .and("leaseOwner").is(owner));
        long matched = mongoTemplate.updateFirst(
                owned,
                update,
                OutboxMessage.class
        ).getMatchedCount();
        if (matched != 1) {
            throw new IllegalStateException(
                    "Outbox message lease was lost"
            );
        }
    }
}
