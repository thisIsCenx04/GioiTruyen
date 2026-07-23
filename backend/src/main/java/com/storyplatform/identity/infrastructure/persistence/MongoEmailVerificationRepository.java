package com.storyplatform.identity.infrastructure.persistence;

import com.storyplatform.identity.application.port.EmailVerificationRepository;
import com.storyplatform.identity.domain.EmailVerification;
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
public class MongoEmailVerificationRepository
        implements EmailVerificationRepository {

    private final MongoTemplate mongoTemplate;

    public MongoEmailVerificationRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = Objects.requireNonNull(
                mongoTemplate,
                "mongoTemplate"
        );
    }

    @Override
    public void save(EmailVerification verification) {
        mongoTemplate.insert(
                MongoEmailVerificationDocument.from(verification)
        );
    }

    @Override
    public Optional<EmailVerification> consume(
            String tokenHash,
            Instant consumedAt
    ) {
        Query eligible = Query.query(new Criteria().andOperator(
                Criteria.where("tokenHash").is(tokenHash),
                Criteria.where("consumedAt").is(null),
                Criteria.where("expiresAt").gt(consumedAt)
        ));
        Update consume = new Update().set("consumedAt", consumedAt);
        MongoEmailVerificationDocument document =
                mongoTemplate.findAndModify(
                        eligible,
                        consume,
                        FindAndModifyOptions.options().returnNew(true),
                        MongoEmailVerificationDocument.class
                );
        return Optional.ofNullable(document)
                .map(MongoEmailVerificationDocument::toDomain);
    }
}
