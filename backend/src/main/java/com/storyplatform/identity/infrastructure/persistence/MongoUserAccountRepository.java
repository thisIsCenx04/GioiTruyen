package com.storyplatform.identity.infrastructure.persistence;

import com.storyplatform.identity.application.port.UserAccountRepository;
import com.storyplatform.identity.domain.UserAccount;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Objects;

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
    public boolean saveIfEmailAvailable(UserAccount account) {
        Objects.requireNonNull(account, "account");
        try {
            mongoTemplate.insert(MongoUserAccountDocument.from(account));
            return true;
        } catch (DuplicateKeyException exception) {
            if (emailExists(account.emailNormalized())) {
                return false;
            }
            throw exception;
        }
    }

    private boolean emailExists(String emailNormalized) {
        Query query = Query.query(
                Criteria.where("emailNormalized").is(emailNormalized)
        );
        return mongoTemplate.exists(
                query,
                MongoUserAccountDocument.COLLECTION
        );
    }
}
