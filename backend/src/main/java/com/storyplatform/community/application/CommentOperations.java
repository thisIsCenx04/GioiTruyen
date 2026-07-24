package com.storyplatform.community.application;

import com.storyplatform.community.domain.Comment;

import java.time.Instant;
import java.util.List;

public interface CommentOperations {

    CommentView create(String userId, CreateCommand command);

    CommentPage list(ListQuery query);

    CommentView update(
            String userId,
            String commentId,
            long expectedVersion,
            String body
    );

    CommentView delete(
            String userId,
            String commentId,
            long expectedVersion
    );

    record CreateCommand(
            Comment.TargetType targetType,
            String targetId,
            String parentId,
            String body
    ) {
    }

    record ListQuery(
            Comment.TargetType targetType,
            String targetId,
            String cursor,
            int limit
    ) {
    }

    record AuthorView(String id, String displayName, String avatarMediaId) {
    }

    record CommentView(
            String id,
            Comment.TargetType targetType,
            String targetId,
            String parentId,
            String rootId,
            int depth,
            AuthorView author,
            String body,
            Comment.Status status,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    record CommentPage(
            List<CommentView> items,
            String nextCursor,
            boolean hasMore
    ) {
    }
}
