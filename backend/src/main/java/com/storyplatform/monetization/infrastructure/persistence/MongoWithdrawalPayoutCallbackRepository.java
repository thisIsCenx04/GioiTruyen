package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.application.WithdrawalException;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutCallbackRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Objects;
import java.util.Optional;

public final class MongoWithdrawalPayoutCallbackRepository
        implements WithdrawalPayoutCallbackRepository {

    private final MongoTemplate mongo;

    public MongoWithdrawalPayoutCallbackRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public Optional<Receipt> find(String provider, String eventId) {
        return Optional.ofNullable(mongo.findOne(
                Query.query(new Criteria().andOperator(
                        Criteria.where("provider").is(provider),
                        Criteria.where("eventId").is(eventId)
                )),
                MongoWithdrawalPayoutCallbackDocument.class
        )).map(MongoWithdrawalPayoutCallbackDocument::toDomain);
    }

    @Override
    public void insert(Receipt receipt) {
        try {
            mongo.insert(MongoWithdrawalPayoutCallbackDocument.from(receipt));
        } catch (DuplicateKeyException exception) {
            throw new WithdrawalException(
                    "Payout callback changed concurrently.",
                    WithdrawalException.Kind.CONFLICT
            );
        }
    }
}
