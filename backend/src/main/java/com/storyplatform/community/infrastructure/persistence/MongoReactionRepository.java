package com.storyplatform.community.infrastructure.persistence;

import com.storyplatform.community.application.port.ReactionRepository;
import com.storyplatform.community.domain.Reaction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Objects;

public final class MongoReactionRepository
        implements ReactionRepository {

    public static final String COLLECTION = "reactions";
    private final MongoTemplate mongo;

    public MongoReactionRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public boolean targetIsVisible(
            Reaction.TargetType targetType,
            String targetId
    ) {
        if (targetType == Reaction.TargetType.COMMENT) {
            return mongo.exists(
                    Query.query(new Criteria().andOperator(
                            Criteria.where("_id").is(targetId),
                            Criteria.where("status").is("VISIBLE")
                    )),
                    MongoCommentRepository.COLLECTION
            );
        }
        String collection = targetType == Reaction.TargetType.STORY
                ? "stories" : "chapters";
        return mongo.exists(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(targetId),
                        Criteria.where("workflowStatus").is("PUBLISHED")
                )),
                collection
        );
    }

    @Override
    public boolean insertIfAbsent(Reaction reaction) {
        return mongo.upsert(
                Query.query(Criteria.where("_id").is(reaction.id())),
                new Update()
                        .setOnInsert("targetType", reaction.targetType().name())
                        .setOnInsert("targetId", reaction.targetId())
                        .setOnInsert("actorId", reaction.actorId())
                        .setOnInsert("createdAt", reaction.createdAt()),
                ReactionDocument.class,
                COLLECTION
        ).getUpsertedId() != null;
    }

    @Override
    public boolean deleteIfPresent(
            Reaction.TargetType targetType,
            String targetId,
            String actorId
    ) {
        return mongo.remove(
                query(targetType, targetId, actorId),
                COLLECTION
        ).getDeletedCount() == 1;
    }

    @Override
    public boolean exists(
            Reaction.TargetType targetType,
            String targetId,
            String actorId
    ) {
        return mongo.exists(
                query(targetType, targetId, actorId),
                COLLECTION
        );
    }

    @Override
    public long count(
            Reaction.TargetType targetType,
            String targetId
    ) {
        return mongo.count(
                Query.query(new Criteria().andOperator(
                        Criteria.where("targetType").is(targetType.name()),
                        Criteria.where("targetId").is(targetId)
                )),
                COLLECTION
        );
    }

    private static Query query(
            Reaction.TargetType targetType,
            String targetId,
            String actorId
    ) {
        return Query.query(new Criteria().andOperator(
                Criteria.where("targetType").is(targetType.name()),
                Criteria.where("targetId").is(targetId),
                Criteria.where("actorId").is(actorId)
        ));
    }

    public record ReactionDocument(
            String id,
            String targetType,
            String targetId,
            String actorId,
            Instant createdAt
    ) {
    }
}
