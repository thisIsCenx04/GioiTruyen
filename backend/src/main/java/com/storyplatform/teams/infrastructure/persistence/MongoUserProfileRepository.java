package com.storyplatform.teams.infrastructure.persistence;

import com.storyplatform.teams.application.port.UserProfileRepository;
import com.storyplatform.teams.domain.UserProfile;
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
public class MongoUserProfileRepository
        implements UserProfileRepository {

    private final MongoTemplate mongoTemplate;

    public MongoUserProfileRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = Objects.requireNonNull(
                mongoTemplate,
                "mongoTemplate"
        );
    }

    @Override
    public UserProfile findOrCreate(
            String userId,
            String defaultDisplayName,
            Instant now
    ) {
        MongoUserProfileDocument document = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(userId)),
                new Update()
                        .setOnInsert("_id", userId)
                        .setOnInsert("displayName", defaultDisplayName)
                        .setOnInsert("bio", "")
                        .setOnInsert("createdAt", now)
                        .setOnInsert("updatedAt", now)
                        .setOnInsert("version", 0),
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                MongoUserProfileDocument.class
        );
        return Objects.requireNonNull(document, "profile upsert").toDomain();
    }

    @Override
    public Optional<UserProfile> findByUserId(String userId) {
        return Optional.ofNullable(mongoTemplate.findById(
                userId,
                MongoUserProfileDocument.class
        )).map(MongoUserProfileDocument::toDomain);
    }

    @Override
    public UpdateResult update(
            String userId,
            long expectedVersion,
            String displayName,
            String bio,
            String avatarMediaId,
            Instant now
    ) {
        Query expected = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(userId),
                Criteria.where("version").is(expectedVersion)
        ));
        Update change = new Update()
                .set("displayName", displayName)
                .set("bio", bio)
                .set("avatarMediaId", avatarMediaId)
                .set("updatedAt", now)
                .inc("version", 1);
        if (mongoTemplate.updateFirst(
                expected,
                change,
                MongoUserProfileDocument.class
        ).getModifiedCount() == 1) {
            return UpdateResult.UPDATED;
        }
        return mongoTemplate.exists(
                Query.query(Criteria.where("_id").is(userId)),
                MongoUserProfileDocument.class
        ) ? UpdateResult.VERSION_CONFLICT : UpdateResult.NOT_FOUND;
    }
}
