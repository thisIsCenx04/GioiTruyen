package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.application.port.TopupSettlementRepository;
import com.storyplatform.monetization.domain.PaymentEvent;
import com.storyplatform.monetization.domain.TopupRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class MongoTopupSettlementRepository
        implements TopupSettlementRepository {

    private final MongoTemplate mongo;

    public MongoTopupSettlementRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public Optional<PaymentEvent> findEvent(
            String provider,
            String eventId
    ) {
        return Optional.ofNullable(mongo.findOne(
                Query.query(Criteria.where("provider").is(provider)
                        .and("providerEventId").is(eventId)),
                MongoPaymentEventDocument.class
        )).map(MongoPaymentEventDocument::toDomain);
    }

    @Override
    public Optional<TopupRequest> findTopup(String transferReference) {
        return Optional.ofNullable(mongo.findOne(
                Query.query(Criteria.where("transferReference")
                        .is(transferReference)),
                MongoTopupRequestDocument.class
        )).map(MongoTopupRequestDocument::toDomain);
    }

    @Override
    public Optional<PaymentEvent> findOldestReceived(String provider) {
        return Optional.ofNullable(mongo.findOne(
                Query.query(Criteria.where("provider").is(provider)
                                .and("status").is("RECEIVED"))
                        .with(Sort.by(
                                Sort.Order.asc("receivedAt"),
                                Sort.Order.asc("_id")
                        )),
                MongoPaymentEventDocument.class
        )).map(MongoPaymentEventDocument::toDomain);
    }

    @Override
    public boolean complete(
            String eventId,
            String topupId,
            String ledgerTransactionId,
            Instant settledAt
    ) {
        var topup = mongo.updateFirst(
                Query.query(Criteria.where("_id").is(topupId)
                        .and("status").is("AWAITING_PAYMENT")),
                new Update()
                        .set("status", "CREDITED")
                        .set("paymentEventId", eventId)
                        .set("ledgerTransactionId", ledgerTransactionId)
                        .set("creditedAt", settledAt),
                MongoTopupRequestDocument.class
        );
        if (topup.getModifiedCount() != 1) {
            return false;
        }
        var event = mongo.updateFirst(
                Query.query(Criteria.where("_id").is(eventId)
                        .and("status").is("RECEIVED")),
                new Update()
                        .set("status", "MATCHED")
                        .set("topupRequestId", topupId)
                        .set("ledgerTransactionId", ledgerTransactionId)
                        .set("settledAt", settledAt),
                MongoPaymentEventDocument.class
        );
        return event.getModifiedCount() == 1;
    }

    @Override
    public boolean flagForReview(
            String eventId,
            String topupId,
            String reason,
            Instant reviewedAt
    ) {
        var event = mongo.updateFirst(
                Query.query(Criteria.where("_id").is(eventId)
                        .and("status").is("RECEIVED")),
                new Update()
                        .set("status", "PENDING_REVIEW")
                        .set("topupRequestId", topupId)
                        .set("reviewReason", reason)
                        .set("reviewedAt", reviewedAt),
                MongoPaymentEventDocument.class
        );
        if (event.getModifiedCount() != 1) {
            return false;
        }
        if (topupId != null) {
            mongo.updateFirst(
                    Query.query(Criteria.where("_id").is(topupId)
                            .and("status").is("AWAITING_PAYMENT")),
                    new Update()
                            .set("status", "PENDING_REVIEW")
                            .set("paymentEventId", eventId)
                            .set("reviewReason", reason)
                            .set("reviewedAt", reviewedAt),
                    MongoTopupRequestDocument.class
            );
        }
        return true;
    }
}
