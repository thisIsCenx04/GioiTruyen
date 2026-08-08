package com.storyplatform.community.application.port;

import com.storyplatform.community.domain.Comment;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface CommentRepository {

    boolean targetIsPublished(Comment.TargetType type, String targetId);

    Optional<Comment> find(String commentId);

    boolean duplicateExists(
            String authorId,
            Comment.TargetType type,
            String targetId,
            String bodyFingerprint,
            Instant since
    );

    void insert(Comment comment, String bodyFingerprint);

    List<Comment> list(
            Comment.TargetType type,
            String targetId,
            Instant beforeCreatedAt,
            String beforeId,
            int limit
    );

    boolean updateOwned(
            String commentId,
            String authorId,
            long expectedVersion,
            String body,
            String bodyFingerprint,
            Instant updatedAt
    );

    boolean deleteOwned(
            String commentId,
            String authorId,
            long expectedVersion,
            Instant updatedAt
    );

    record Author(String id, String displayName, String avatarMediaId) {
    }

    Author author(String userId);

    Map<String, Author> authors(Set<String> userIds);
}
