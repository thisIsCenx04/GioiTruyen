package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.application.DonationException;
import com.storyplatform.monetization.application.port.DonationRepository;
import com.storyplatform.monetization.domain.Donation;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Objects;
import java.util.Optional;

public final class MongoDonationRepository
        implements DonationRepository {

    private final MongoTemplate mongo;

    public MongoDonationRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public Optional<Donation> findByIdempotencyKeyHash(String keyHash) {
        return Optional.ofNullable(mongo.findOne(
                Query.query(Criteria.where("idempotencyKeyHash").is(keyHash)),
                MongoDonationDocument.class
        )).map(MongoDonationDocument::toDomain);
    }

    @Override
    public Donation insert(Donation donation) {
        try {
            return mongo.insert(MongoDonationDocument.from(donation))
                    .toDomain();
        } catch (DuplicateKeyException exception) {
            throw new DonationException(
                    "Donation idempotency key already exists.",
                    DonationException.Kind.CONFLICT
            );
        }
    }
}
