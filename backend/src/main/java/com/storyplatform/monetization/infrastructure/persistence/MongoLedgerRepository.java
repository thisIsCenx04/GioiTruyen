package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.application.port.LedgerRepository;
import com.storyplatform.monetization.domain.LedgerTransaction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Objects;
import java.util.Optional;

public final class MongoLedgerRepository implements LedgerRepository {

    private final MongoTemplate mongo;

    public MongoLedgerRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public Optional<LedgerTransaction> findByIdempotencyKeyHash(
            String keyHash
    ) {
        return Optional.ofNullable(mongo.findOne(
                Query.query(Criteria.where("idempotencyKeyHash").is(keyHash)),
                MongoLedgerTransactionDocument.class
        )).map(MongoLedgerTransactionDocument::toDomain);
    }

    @Override
    public LedgerTransaction insert(LedgerTransaction transaction) {
        return mongo.insert(
                MongoLedgerTransactionDocument.from(transaction)
        ).toDomain();
    }
}
