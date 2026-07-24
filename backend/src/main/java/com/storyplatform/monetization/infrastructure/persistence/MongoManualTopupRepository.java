package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.application.port.ManualTopupRepository;
import com.storyplatform.monetization.domain.PaymentEvent;
import com.storyplatform.monetization.domain.TopupRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MongoManualTopupRepository
        implements ManualTopupRepository {

    private final MongoTemplate mongo;

    public MongoManualTopupRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public Optional<TopupRequest> findPendingTopup(String topupId) {
        return Optional.ofNullable(mongo.findOne(
                Query.query(Criteria.where("_id").is(topupId)
                        .and("status").is("PENDING_REVIEW")),
                MongoTopupRequestDocument.class
        )).map(MongoTopupRequestDocument::toDomain);
    }

    @Override
    public Optional<PaymentEvent> findPendingEvent(String topupId) {
        return Optional.ofNullable(mongo.findOne(
                Query.query(Criteria.where("topupRequestId").is(topupId)
                        .and("status").is("PENDING_REVIEW")),
                MongoPaymentEventDocument.class
        )).map(MongoPaymentEventDocument::toDomain);
    }

    @Override
    public boolean complete(
            String topupId,
            String eventId,
            String ledgerTransactionId,
            String actorId,
            String reason,
            String evidenceReference,
            Instant decidedAt
    ) {
        var topup = mongo.updateFirst(
                Query.query(Criteria.where("_id").is(topupId)
                        .and("status").is("PENDING_REVIEW")),
                new Update()
                        .set("status", "CREDITED")
                        .set("ledgerTransactionId", ledgerTransactionId)
                        .set("manualDecisionBy", actorId)
                        .set("creditedAt", decidedAt),
                MongoTopupRequestDocument.class
        );
        if (topup.getModifiedCount() != 1) {
            return false;
        }
        var event = mongo.updateFirst(
                Query.query(Criteria.where("_id").is(eventId)
                        .and("status").is("PENDING_REVIEW")),
                new Update()
                        .set("status", "MATCHED")
                        .set("ledgerTransactionId", ledgerTransactionId)
                        .set("manualDecisionBy", actorId)
                        .set("settledAt", decidedAt),
                MongoPaymentEventDocument.class
        );
        if (event.getModifiedCount() != 1) {
            return false;
        }
        mongo.insert(new MongoManualTopupAuditDocument(
                UUID.randomUUID().toString(),
                "topup.manual_approved",
                topupId,
                eventId,
                ledgerTransactionId,
                actorId,
                reason,
                evidenceReference,
                decidedAt
        ));
        return true;
    }
}
