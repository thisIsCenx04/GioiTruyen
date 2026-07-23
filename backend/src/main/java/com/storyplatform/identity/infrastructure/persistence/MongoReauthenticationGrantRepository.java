package com.storyplatform.identity.infrastructure.persistence;

import com.storyplatform.identity.application.ReauthenticationScope;
import com.storyplatform.identity.application.port
        .ReauthenticationGrantRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Objects;

@Repository
public class MongoReauthenticationGrantRepository
        implements ReauthenticationGrantRepository {

    private final MongoTemplate mongoTemplate;

    public MongoReauthenticationGrantRepository(
            MongoTemplate mongoTemplate
    ) {
        this.mongoTemplate = Objects.requireNonNull(
                mongoTemplate,
                "mongoTemplate"
        );
    }

    @Override
    public void save(Grant grant) {
        mongoTemplate.insert(
                MongoReauthenticationGrantDocument.from(grant)
        );
    }

    @Override
    public boolean consume(
            String tokenHash,
            String actorId,
            ReauthenticationScope scope,
            String targetType,
            String targetId,
            Instant consumedAt
    ) {
        Query eligible = Query.query(new Criteria().andOperator(
                Criteria.where("tokenHash").is(tokenHash),
                Criteria.where("actorId").is(actorId),
                Criteria.where("scope").is(scope),
                Criteria.where("targetType").is(targetType),
                Criteria.where("targetId").is(targetId),
                Criteria.where("consumedAt").is(null),
                Criteria.where("expiresAt").gt(consumedAt)
        ));
        return mongoTemplate.updateFirst(
                eligible,
                new Update().set("consumedAt", consumedAt),
                MongoReauthenticationGrantDocument.class
        ).getModifiedCount() == 1;
    }
}
