package com.storyplatform.identity.infrastructure.persistence;

import com.storyplatform.identity.application.port.PasswordResetRepository;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MongoPasswordResetRepository
        implements PasswordResetRepository {

    private final MongoTemplate mongoTemplate;

    public MongoPasswordResetRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = Objects.requireNonNull(
                mongoTemplate,
                "mongoTemplate"
        );
    }

    @Override
    public void save(PasswordReset reset) {
        mongoTemplate.insert(MongoPasswordResetDocument.from(reset));
    }

    @Override
    public Optional<PasswordReset> consume(
            String tokenHash,
            Instant consumedAt
    ) {
        Query eligible = Query.query(new Criteria().andOperator(
                Criteria.where("tokenHash").is(tokenHash),
                Criteria.where("consumedAt").is(null),
                Criteria.where("expiresAt").gt(consumedAt)
        ));
        MongoPasswordResetDocument consumed = mongoTemplate.findAndModify(
                eligible,
                new Update().set("consumedAt", consumedAt),
                FindAndModifyOptions.options().returnNew(true),
                MongoPasswordResetDocument.class
        );
        return Optional.ofNullable(consumed)
                .map(MongoPasswordResetDocument::toDomain);
    }
}
