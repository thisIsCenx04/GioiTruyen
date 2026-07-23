package com.storyplatform.identity.infrastructure.persistence;

import com.storyplatform.identity.application.port
        .RefreshTokenFamilyRepository;
import com.storyplatform.identity.domain.RefreshTokenFamily;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Objects;

@Repository
public class MongoRefreshTokenFamilyRepository
        implements RefreshTokenFamilyRepository {

    public static final String REUSE_REASON = "REUSE_DETECTED";

    private final MongoTemplate mongoTemplate;

    public MongoRefreshTokenFamilyRepository(
            MongoTemplate mongoTemplate
    ) {
        this.mongoTemplate = Objects.requireNonNull(
                mongoTemplate,
                "mongoTemplate"
        );
    }

    @Override
    public void create(RefreshTokenFamily family) {
        mongoTemplate.insert(
                MongoRefreshTokenFamilyDocument.from(family)
        );
    }

    @Override
    public RotationResult rotate(
            String currentTokenHash,
            String nextTokenHash,
            Instant now,
            int maximumGeneration
    ) {
        Query eligible = Query.query(new Criteria().andOperator(
                Criteria.where("currentTokenHash").is(currentTokenHash),
                Criteria.where("revokedAt").is(null),
                Criteria.where("expiresAt").gt(now),
                Criteria.where("generation").lt(maximumGeneration)
        ));
        Update rotate = new Update()
                .push("usedTokenHashes", currentTokenHash)
                .set("currentTokenHash", nextTokenHash)
                .set("updatedAt", now)
                .inc("generation", 1);
        MongoRefreshTokenFamilyDocument rotated =
                mongoTemplate.findAndModify(
                        eligible,
                        rotate,
                        FindAndModifyOptions.options().returnNew(true),
                        MongoRefreshTokenFamilyDocument.class
                );
        if (rotated != null) {
            return RotationResult.rotated(
                    rotated.id(),
                    rotated.userId(),
                    rotated.securityVersion()
            );
        }

        Query replayed = Query.query(new Criteria().andOperator(
                Criteria.where("usedTokenHashes").is(currentTokenHash),
                Criteria.where("revokedAt").is(null)
        ));
        Update revoke = new Update()
                .set("revokedAt", now)
                .set("revokeReason", REUSE_REASON)
                .set("updatedAt", now);
        MongoRefreshTokenFamilyDocument compromised =
                mongoTemplate.findAndModify(
                        replayed,
                        revoke,
                        FindAndModifyOptions.options().returnNew(true),
                        MongoRefreshTokenFamilyDocument.class
                );
        return compromised == null
                ? RotationResult.invalid()
                : RotationResult.reuseDetected(compromised.id());
    }

    @Override
    public void revoke(
            String familyId,
            Instant revokedAt,
            String reason
    ) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(familyId)),
                new Update()
                        .set("revokedAt", revokedAt)
                        .set("revokeReason", reason)
                        .set("updatedAt", revokedAt),
                MongoRefreshTokenFamilyDocument.class
        );
    }
}
