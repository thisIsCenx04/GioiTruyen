package com.storyplatform.identity.infrastructure.persistence;

import com.storyplatform.identity.application.port.MfaFactorRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MongoMfaFactorRepository implements MfaFactorRepository {

    private final MongoTemplate mongoTemplate;

    public MongoMfaFactorRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = Objects.requireNonNull(
                mongoTemplate,
                "mongoTemplate"
        );
    }

    @Override
    public Optional<Factor> findByUserId(String userId) {
        return Optional.ofNullable(mongoTemplate.findById(
                userId,
                MongoMfaFactorDocument.class
        )).map(document -> new Factor(
                document.userId(),
                document.protectedSecret(),
                document.enabled()
        ));
    }

    @Override
    public boolean savePending(
            String userId,
            String protectedSecret,
            Instant createdAt
    ) {
        try {
            var result = mongoTemplate.upsert(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(userId),
                        Criteria.where("enabled").ne(true)
                )),
                new Update()
                        .set("protectedSecret", protectedSecret)
                        .set("enabled", false)
                        .set("recoveryCodeHashes", List.of())
                        .set("createdAt", createdAt)
                        .unset("activatedAt")
                        .set("updatedAt", createdAt),
                    MongoMfaFactorDocument.class
            );
            return result.getModifiedCount() == 1
                    || result.getUpsertedId() != null;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public boolean activate(
            String userId,
            List<String> recoveryCodeHashes,
            Instant activatedAt
    ) {
        return mongoTemplate.updateFirst(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(userId),
                        Criteria.where("enabled").is(false)
                )),
                new Update()
                        .set("enabled", true)
                        .set("recoveryCodeHashes", recoveryCodeHashes)
                        .set("activatedAt", activatedAt)
                        .set("updatedAt", activatedAt),
                MongoMfaFactorDocument.class
        ).getModifiedCount() == 1;
    }

    @Override
    public boolean consumeRecoveryCode(
            String userId,
            String recoveryCodeHash,
            Instant consumedAt
    ) {
        return mongoTemplate.updateFirst(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(userId),
                        Criteria.where("enabled").is(true),
                        Criteria.where("recoveryCodeHashes")
                                .is(recoveryCodeHash)
                )),
                new Update()
                        .pull("recoveryCodeHashes", recoveryCodeHash)
                        .set("updatedAt", consumedAt),
                MongoMfaFactorDocument.class
        ).getModifiedCount() == 1;
    }
}
