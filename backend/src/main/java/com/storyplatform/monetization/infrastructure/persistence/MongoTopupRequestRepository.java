package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.application.TopupRequestException;
import com.storyplatform.monetization.application.port.TopupRequestRepository;
import com.storyplatform.monetization.domain.TopupRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MongoTopupRequestRepository
        implements TopupRequestRepository {

    private final MongoTemplate mongo;

    public MongoTopupRequestRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public Optional<TopupRequest> findByIdempotencyKeyHash(
            String keyHash
    ) {
        return Optional.ofNullable(mongo.findOne(
                Query.query(
                        Criteria.where("idempotencyKeyHash").is(keyHash)
                ),
                MongoTopupRequestDocument.class
        )).map(MongoTopupRequestDocument::toDomain);
    }

    @Override
    public Optional<TopupRequest> findByIdAndUserId(
            String id,
            String userId
    ) {
        return Optional.ofNullable(mongo.findOne(
                Query.query(Criteria.where("_id").is(id)
                        .and("userId").is(userId)),
                MongoTopupRequestDocument.class
        )).map(MongoTopupRequestDocument::toDomain);
    }

    @Override
    public List<TopupRequest> findRecentByUserId(
            String userId,
            int limit
    ) {
        Query query = Query.query(Criteria.where("userId").is(userId))
                .with(Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("_id")
                ))
                .limit(limit);
        return mongo.find(query, MongoTopupRequestDocument.class)
                .stream()
                .map(MongoTopupRequestDocument::toDomain)
                .toList();
    }

    @Override
    public TopupRequest insert(TopupRequest request) {
        try {
            return mongo.insert(MongoTopupRequestDocument.from(request))
                    .toDomain();
        } catch (DuplicateKeyException exception) {
            throw new TopupRequestException(
                    "Top-up idempotency key or transfer reference conflicts.",
                    TopupRequestException.Kind.CONFLICT
            );
        }
    }
}
