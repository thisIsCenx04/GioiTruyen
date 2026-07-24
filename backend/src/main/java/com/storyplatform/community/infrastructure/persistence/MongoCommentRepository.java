package com.storyplatform.community.infrastructure.persistence;

import com.storyplatform.community.application.port.CommentRepository;
import com.storyplatform.community.domain.Comment;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class MongoCommentRepository implements CommentRepository {

    public static final String COLLECTION = "comments";
    private final MongoTemplate mongo;

    public MongoCommentRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public boolean targetIsPublished(
            Comment.TargetType type,
            String targetId
    ) {
        String collection = type == Comment.TargetType.STORY
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
    public Optional<Comment> find(String commentId) {
        return Optional.ofNullable(mongo.findById(
                commentId,
                CommentDocument.class,
                COLLECTION
        )).map(CommentDocument::toDomain);
    }

    @Override
    public boolean duplicateExists(
            String authorId,
            Comment.TargetType type,
            String targetId,
            String bodyFingerprint,
            Instant since
    ) {
        return mongo.exists(
                Query.query(new Criteria().andOperator(
                        Criteria.where("authorId").is(authorId),
                        Criteria.where("targetType").is(type.name()),
                        Criteria.where("targetId").is(targetId),
                        Criteria.where("bodyFingerprint")
                                .is(bodyFingerprint),
                        Criteria.where("createdAt").gte(since),
                        Criteria.where("status").ne(
                                Comment.Status.DELETED.name()
                        )
                )),
                COLLECTION
        );
    }

    @Override
    public void insert(Comment comment, String bodyFingerprint) {
        mongo.insert(CommentDocument.from(comment, bodyFingerprint), COLLECTION);
    }

    @Override
    public List<Comment> list(
            Comment.TargetType type,
            String targetId,
            Instant beforeCreatedAt,
            String beforeId,
            int limit
    ) {
        Criteria target = new Criteria().andOperator(
                Criteria.where("targetType").is(type.name()),
                Criteria.where("targetId").is(targetId),
                Criteria.where("status").in(
                        Comment.Status.VISIBLE.name(),
                        Comment.Status.DELETED.name()
                )
        );
        Criteria criteria = target;
        if (beforeCreatedAt != null) {
            criteria = new Criteria().andOperator(
                    target,
                    new Criteria().orOperator(
                            Criteria.where("createdAt").lt(beforeCreatedAt),
                            new Criteria().andOperator(
                                    Criteria.where("createdAt")
                                            .is(beforeCreatedAt),
                                    Criteria.where("_id").lt(beforeId)
                            )
                    )
            );
        }
        return mongo.find(
                        Query.query(criteria)
                                .with(Sort.by(
                                        Sort.Order.desc("createdAt"),
                                        Sort.Order.desc("_id")
                                ))
                                .limit(limit),
                        CommentDocument.class,
                        COLLECTION
                ).stream()
                .map(CommentDocument::toDomain)
                .toList();
    }

    @Override
    public boolean updateOwned(
            String commentId,
            String authorId,
            long expectedVersion,
            String body,
            String bodyFingerprint,
            Instant updatedAt
    ) {
        return mongo.updateFirst(
                owned(commentId, authorId, expectedVersion),
                new Update()
                        .set("body", body)
                        .set("bodyFingerprint", bodyFingerprint)
                        .set("updatedAt", updatedAt)
                        .inc("version", 1),
                COLLECTION
        ).getModifiedCount() == 1;
    }

    @Override
    public boolean deleteOwned(
            String commentId,
            String authorId,
            long expectedVersion,
            Instant updatedAt
    ) {
        return mongo.updateFirst(
                owned(commentId, authorId, expectedVersion),
                new Update()
                        .set("body", "")
                        .unset("bodyFingerprint")
                        .set("status", Comment.Status.DELETED.name())
                        .set("updatedAt", updatedAt)
                        .set("deletedAt", updatedAt)
                        .inc("version", 1),
                COLLECTION
        ).getModifiedCount() == 1;
    }

    @Override
    public Author author(String userId) {
        Profile profile = mongo.findById(
                userId,
                Profile.class,
                "user_profiles"
        );
        return profile == null
                ? new Author(userId, "Độc giả", null)
                : new Author(userId, profile.displayName(), profile.avatarMediaId());
    }

    @Override
    public Map<String, Author> authors(Set<String> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Author> found = mongo.find(
                        Query.query(Criteria.where("_id").in(userIds)),
                        Profile.class,
                        "user_profiles"
                ).stream()
                .map(profile -> new Author(
                        profile.id(),
                        profile.displayName(),
                        profile.avatarMediaId()
                ))
                .collect(Collectors.toMap(Author::id, Function.identity()));
        return userIds.stream().collect(Collectors.toUnmodifiableMap(
                Function.identity(),
                id -> found.getOrDefault(
                        id,
                        new Author(id, "Độc giả", null)
                )
        ));
    }

    private static Query owned(
            String commentId,
            String authorId,
            long version
    ) {
        return Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(commentId),
                Criteria.where("authorId").is(authorId),
                Criteria.where("version").is(version),
                Criteria.where("status").ne(Comment.Status.DELETED.name())
        ));
    }

    public record CommentDocument(
            String id,
            String targetType,
            String targetId,
            String parentId,
            String rootId,
            int depth,
            String authorId,
            String body,
            String bodyFingerprint,
            String status,
            long version,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt
    ) {
        static CommentDocument from(
                Comment value,
                String bodyFingerprint
        ) {
            return new CommentDocument(
                    value.id(),
                    value.targetType().name(),
                    value.targetId(),
                    value.parentId(),
                    value.rootId(),
                    value.depth(),
                    value.authorId(),
                    value.body(),
                    bodyFingerprint,
                    value.status().name(),
                    value.version(),
                    value.createdAt(),
                    value.updatedAt(),
                    null
            );
        }

        Comment toDomain() {
            return new Comment(
                    id,
                    Comment.TargetType.valueOf(targetType),
                    targetId,
                    parentId,
                    rootId,
                    depth,
                    authorId,
                    body,
                    Comment.Status.valueOf(status),
                    version,
                    createdAt,
                    updatedAt
            );
        }
    }

    public record Profile(
            String id,
            String displayName,
            String avatarMediaId
    ) {
    }
}
