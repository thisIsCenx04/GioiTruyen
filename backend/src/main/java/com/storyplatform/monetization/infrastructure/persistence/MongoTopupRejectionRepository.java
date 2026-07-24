package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.application.port.TopupRejectionRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MongoTopupRejectionRepository
        implements TopupRejectionRepository {

    private final MongoTemplate mongo;

    public MongoTopupRejectionRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public Optional<Review> find(String topupId) {
        var topup = mongo.findById(
                topupId,
                MongoTopupRequestDocument.class
        );
        if (topup == null) {
            return Optional.empty();
        }
        var event = mongo.findOne(
                Query.query(Criteria.where("topupRequestId").is(topupId)),
                MongoPaymentEventDocument.class
        );
        if (event == null) {
            return Optional.empty();
        }
        return Optional.of(new Review(
                topup.toDomain(),
                event.toDomain()
        ));
    }

    @Override
    public boolean reject(
            Review review,
            String actorId,
            String reasonCode,
            String reason,
            String evidenceReference,
            Instant decidedAt
    ) {
        var topup = mongo.updateFirst(
                Query.query(Criteria.where("_id").is(review.topup().id())
                        .and("status").is("PENDING_REVIEW")),
                new Update()
                        .set("status", "REJECTED")
                        .set("manualDecisionBy", actorId)
                        .set("rejectedAt", decidedAt),
                MongoTopupRequestDocument.class
        );
        if (topup.getModifiedCount() != 1) {
            return false;
        }
        var event = mongo.updateFirst(
                Query.query(Criteria.where("_id")
                        .is(review.paymentEvent().id())
                        .and("status").is("PENDING_REVIEW")),
                new Update()
                        .set("status", "REJECTED")
                        .set("manualDecisionBy", actorId)
                        .set("rejectedAt", decidedAt),
                MongoPaymentEventDocument.class
        );
        if (event.getModifiedCount() != 1) {
            return false;
        }
        mongo.insert(new MongoManualTopupAuditDocument(
                UUID.randomUUID().toString(),
                "topup.rejected",
                review.topup().id(),
                review.paymentEvent().id(),
                null,
                actorId,
                reasonCode + ": " + reason,
                evidenceReference,
                decidedAt
        ));
        return true;
    }
}
