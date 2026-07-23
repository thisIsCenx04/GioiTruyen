package com.storyplatform.identity.infrastructure.persistence;

import com.storyplatform.identity.application.port.UserAccountRepository;
import com.storyplatform.identity.domain.UserAccount;
import com.storyplatform.identity.domain.UserState;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MongoUserAccountRepository implements UserAccountRepository {

    private final MongoTemplate mongoTemplate;

    public MongoUserAccountRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = Objects.requireNonNull(
                mongoTemplate,
                "mongoTemplate"
        );
    }

    @Override
    public boolean activatePending(String userId, Instant activatedAt) {
        Query pendingAccount = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(userId),
                Criteria.where("state").is(
                        UserState.PENDING_EMAIL_VERIFICATION
                )
        ));
        Update activate = new Update()
                .set("state", UserState.ACTIVE)
                .set("updatedAt", activatedAt)
                .inc("version", 1);
        return mongoTemplate.updateFirst(
                pendingAccount,
                activate,
                MongoUserAccountDocument.class
        ).getModifiedCount() == 1;
    }

    @Override
    public boolean resetPassword(
            String userId,
            String passwordHash,
            Instant changedAt
    ) {
        Query activeAccount = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(userId),
                Criteria.where("state").is(UserState.ACTIVE)
        ));
        Update reset = new Update()
                .set("passwordHash", passwordHash)
                .set("updatedAt", changedAt)
                .inc("securityVersion", 1)
                .inc("version", 1);
        return mongoTemplate.updateFirst(
                activeAccount,
                reset,
                MongoUserAccountDocument.class
        ).getModifiedCount() == 1;
    }

    @Override
    public boolean incrementSecurityVersion(
            String userId,
            Instant changedAt
    ) {
        return mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(userId)),
                new Update()
                        .set("updatedAt", changedAt)
                        .inc("securityVersion", 1)
                        .inc("version", 1),
                MongoUserAccountDocument.class
        ).getModifiedCount() == 1;
    }

    @Override
    public Optional<UserAccount> findByEmail(String emailNormalized) {
        Query email = Query.query(
                Criteria.where("emailNormalized").is(emailNormalized)
        );
        return Optional.ofNullable(mongoTemplate.findOne(
                email,
                MongoUserAccountDocument.class
        )).map(MongoUserAccountDocument::toDomain);
    }

    @Override
    public Optional<UserAccount> findById(String userId) {
        return Optional.ofNullable(mongoTemplate.findById(
                userId,
                MongoUserAccountDocument.class
        )).map(MongoUserAccountDocument::toDomain);
    }

    @Override
    public boolean saveIfEmailAvailable(UserAccount account) {
        Objects.requireNonNull(account, "account");
        Query email = Query.query(Criteria.where("emailNormalized")
                .is(account.emailNormalized()));
        Update createOnly = new Update()
                .setOnInsert("_id", account.id())
                .setOnInsert(
                        "emailNormalized",
                        account.emailNormalized()
                )
                .setOnInsert("passwordHash", account.passwordHash())
                .setOnInsert("globalRoles", account.globalRoles())
                .setOnInsert("state", account.state())
                .setOnInsert(
                        "securityVersion",
                        account.securityVersion()
                )
                .setOnInsert(
                        "acceptedConsentVersion",
                        account.acceptedConsentVersion()
                )
                .setOnInsert(
                        "consentAcceptedAt",
                        account.consentAcceptedAt()
                )
                .setOnInsert("createdAt", account.createdAt())
                .setOnInsert("updatedAt", account.updatedAt())
                .setOnInsert("version", account.version());
        return mongoTemplate.upsert(
                email,
                createOnly,
                MongoUserAccountDocument.class
        ).getUpsertedId() != null;
    }
}
