package com.storyplatform.community.infrastructure.persistence;

import com.storyplatform.community.application.port.CommentRepository;
import com.storyplatform.community.domain.Comment;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DisabledCommentRepository implements CommentRepository {

    @Override
    public boolean targetIsPublished(Comment.TargetType type, String targetId) {
        return true;
    }

    @Override
    public Optional<Comment> find(String commentId) {
        return Optional.empty();
    }

    @Override
    public boolean duplicateExists(
            String authorId,
            Comment.TargetType type,
            String targetId,
            String bodyFingerprint,
            Instant since
    ) {
        return false;
    }

    @Override
    public void insert(Comment comment, String bodyFingerprint) {
    }

    @Override
    public List<Comment> list(
            Comment.TargetType type,
            String targetId,
            Instant beforeCreatedAt,
            String beforeId,
            int limit
    ) {
        return List.of();
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
        return false;
    }

    @Override
    public boolean deleteOwned(
            String commentId,
            String authorId,
            long expectedVersion,
            Instant updatedAt
    ) {
        return false;
    }

    @Override
    public Author author(String userId) {
        return new Author(userId, "User", null);
    }

    @Override
    public Map<String, Author> authors(Set<String> userIds) {
        return Map.of();
    }
}
