package com.storyplatform.community.infrastructure.persistence;

import com.storyplatform.community.application.port.StoryRelationRepository;
import com.storyplatform.community.domain.StoryRelation;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Objects;

public final class MongoStoryRelationRepository
        implements StoryRelationRepository {

    public static final String COLLECTION = "story_relations";
    private final MongoTemplate mongo;

    public MongoStoryRelationRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public boolean storyIsPublished(String storyId) {
        return mongo.exists(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(storyId),
                        Criteria.where("state").is("PUBLISHED")
                )),
                "stories"
        );
    }

    @Override
    public boolean insertIfAbsent(StoryRelation relation) {
        return mongo.upsert(
                Query.query(Criteria.where("_id").is(relation.id())),
                new Update()
                        .setOnInsert("storyId", relation.storyId())
                        .setOnInsert("userId", relation.userId())
                        .setOnInsert("type", relation.type().name())
                        .setOnInsert("createdAt", relation.createdAt()),
                RelationDocument.class,
                COLLECTION
        ).getUpsertedId() != null;
    }

    @Override
    public boolean deleteIfPresent(
            String storyId,
            String userId,
            StoryRelation.Type type
    ) {
        return mongo.remove(
                query(storyId, userId, type),
                COLLECTION
        ).getDeletedCount() == 1;
    }

    @Override
    public boolean exists(
            String storyId,
            String userId,
            StoryRelation.Type type
    ) {
        return mongo.exists(
                query(storyId, userId, type),
                COLLECTION
        );
    }

    @Override
    public long count(String storyId, StoryRelation.Type type) {
        return mongo.count(
                Query.query(new Criteria().andOperator(
                        Criteria.where("storyId").is(storyId),
                        Criteria.where("type").is(type.name())
                )),
                COLLECTION
        );
    }

    private static Query query(
            String storyId,
            String userId,
            StoryRelation.Type type
    ) {
        return Query.query(new Criteria().andOperator(
                Criteria.where("storyId").is(storyId),
                Criteria.where("userId").is(userId),
                Criteria.where("type").is(type.name())
        ));
    }

    public record RelationDocument(
            String id,
            String storyId,
            String userId,
            String type,
            Instant createdAt
    ) {
    }
}
